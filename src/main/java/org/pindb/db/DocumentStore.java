package org.pindb.db;

import org.pindb.model.DocumentData;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class DocumentStore implements AutoCloseable {
    private final Connection connection;

    public DocumentStore(Path databasePath) {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath.toAbsolutePath().normalize());
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys=ON");
                statement.execute("PRAGMA busy_timeout=5000");
                statement.execute("CREATE TABLE IF NOT EXISTS document_values ("
                        + "record_id INTEGER NOT NULL, field_id INTEGER NOT NULL, file_name TEXT NOT NULL, "
                        + "mime_type TEXT NOT NULL, file_size INTEGER NOT NULL, data BLOB NOT NULL, created_at TEXT NOT NULL, "
                        + "PRIMARY KEY(record_id,field_id), "
                        + "FOREIGN KEY(record_id) REFERENCES records(id) ON DELETE CASCADE, "
                        + "FOREIGN KEY(field_id) REFERENCES field_definitions(id) ON DELETE CASCADE)");
                statement.execute("CREATE TABLE IF NOT EXISTS backup_document_values ("
                        + "snapshot_id INTEGER NOT NULL, record_id INTEGER NOT NULL, field_id INTEGER NOT NULL, "
                        + "file_name TEXT NOT NULL, mime_type TEXT NOT NULL, file_size INTEGER NOT NULL, "
                        + "data BLOB NOT NULL, created_at TEXT NOT NULL, "
                        + "PRIMARY KEY(snapshot_id,record_id,field_id))");
                statement.execute("CREATE TRIGGER IF NOT EXISTS backup_documents_after_snapshot "
                        + "AFTER INSERT ON backup_snapshots BEGIN "
                        + "INSERT INTO backup_document_values(snapshot_id,record_id,field_id,file_name,mime_type,file_size,data,created_at) "
                        + "SELECT NEW.id,record_id,field_id,file_name,mime_type,file_size,data,created_at FROM document_values; END");
                statement.execute("CREATE TRIGGER IF NOT EXISTS delete_backup_documents_after_snapshot "
                        + "AFTER DELETE ON backup_snapshots BEGIN "
                        + "DELETE FROM backup_document_values WHERE snapshot_id=OLD.id; END");
            }
        } catch (SQLException | ClassNotFoundException exception) {
            throw new DatabaseException("Could not initialize embedded document storage.", exception);
        }
    }

    public Map<Long, DocumentData> documentsForRecord(long recordId) {
        LinkedHashMap<Long, DocumentData> documents = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT field_id,file_name,mime_type,data FROM document_values WHERE record_id=? ORDER BY field_id")) {
            statement.setLong(1, recordId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    documents.put(result.getLong("field_id"), new DocumentData(
                            result.getString("file_name"), result.getString("mime_type"), result.getBytes("data")));
                }
            }
            return Map.copyOf(documents);
        } catch (SQLException exception) {
            throw new DatabaseException("Could not load documents for entry " + recordId + ".", exception);
        }
    }

    public Optional<DocumentData> document(long recordId, long fieldId) {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT file_name,mime_type,data FROM document_values WHERE record_id=? AND field_id=?")) {
            statement.setLong(1, recordId);
            statement.setLong(2, fieldId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(new DocumentData(result.getString("file_name"),
                        result.getString("mime_type"), result.getBytes("data")));
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Could not load the selected document.", exception);
        }
    }

    public void replaceDocuments(long recordId, Map<Long, DocumentData> documents) {
        long recoverySnapshot = latestSnapshotId();
        boolean previousAutoCommit;
        try {
            previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM document_values WHERE record_id=?")) {
                delete.setLong(1, recordId);
                delete.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO document_values(record_id,field_id,file_name,mime_type,file_size,data,created_at) "
                            + "VALUES(?,?,?,?,?,?,?)")) {
                for (Map.Entry<Long, DocumentData> entry : documents.entrySet()) {
                    DocumentData document = entry.getValue();
                    if (document == null || document.data().length == 0) {
                        continue;
                    }
                    insert.setLong(1, recordId);
                    insert.setLong(2, entry.getKey());
                    insert.setString(3, document.fileName());
                    insert.setString(4, document.mimeType());
                    insert.setLong(5, document.size());
                    insert.setBytes(6, document.data());
                    insert.setString(7, LocalDateTime.now().toString());
                    insert.addBatch();
                }
                insert.executeBatch();
            }
            connection.commit();
            connection.setAutoCommit(previousAutoCommit);
        } catch (SQLException exception) {
            rollbackQuietly();
            DatabaseException failure = new DatabaseException(
                    "Could not save embedded documents for entry " + recordId + ". The entry changes were rolled back.",
                    exception);
            if (recoverySnapshot > 0) {
                try {
                    restoreDatabaseFromSnapshot(recoverySnapshot);
                } catch (DatabaseException recoveryFailure) {
                    failure = new DatabaseException(
                            "Could not save embedded documents for entry " + recordId
                                    + ", and PinDB could not fully restore the previous snapshot. "
                                    + "Do not make further changes until the database is checked or restored from backup.",
                            exception);
                    failure.addSuppressed(recoveryFailure);
                }
            }
            throw failure;
        }
    }

    public void restoreSnapshot(long snapshotId) {
        boolean previousAutoCommit;
        try {
            previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            restoreDocumentsFromSnapshot(snapshotId);
            connection.commit();
            connection.setAutoCommit(previousAutoCommit);
        } catch (SQLException exception) {
            rollbackQuietly();
            throw new DatabaseException("Could not restore embedded documents from the selected backup.", exception);
        }
    }

    private long latestSnapshotId() {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT id FROM backup_snapshots ORDER BY id DESC LIMIT 1")) {
            return result.next() ? result.getLong(1) : -1;
        } catch (SQLException exception) {
            throw new DatabaseException("Could not locate the recovery snapshot before saving embedded documents.", exception);
        }
    }

    private void restoreDatabaseFromSnapshot(long snapshotId) {
        try {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("PRAGMA defer_foreign_keys=ON");
                    statement.executeUpdate("DELETE FROM document_values");
                    statement.executeUpdate("DELETE FROM record_values");
                    statement.executeUpdate("DELETE FROM records");
                    statement.executeUpdate("DELETE FROM field_definitions");
                    statement.executeUpdate("DELETE FROM pindb_meta");
                }
                restoreCoreTable(snapshotId, "pindb_meta", "key,value",
                        "SELECT key,value FROM backup_meta WHERE snapshot_id=?");
                restoreCoreTable(snapshotId, "field_definitions",
                        "id,name,field_type,position,required,default_value,min_value,max_value,unique_value,char_limit,dropdown_options,summary_type",
                        "SELECT field_id,name,field_type,position,required,default_value,min_value,max_value,unique_value,char_limit,dropdown_options,summary_type "
                                + "FROM backup_fields WHERE snapshot_id=?");
                restoreCoreTable(snapshotId, "records", "id,created_at,updated_at,deleted_at",
                        "SELECT record_id,created_at,updated_at,deleted_at FROM backup_records WHERE snapshot_id=?");
                restoreCoreTable(snapshotId, "record_values", "record_id,field_id,value",
                        "SELECT record_id,field_id,value FROM backup_values WHERE snapshot_id=?");
                restoreDocumentsFromSnapshot(snapshotId);
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Could not restore the database after the document save failed.", exception);
        }
    }

    private void restoreCoreTable(long snapshotId, String table, String columns, String select) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO " + table + "(" + columns + ") " + select)) {
            statement.setLong(1, snapshotId);
            statement.executeUpdate();
        }
    }

    private void restoreDocumentsFromSnapshot(long snapshotId) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM document_values");
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO document_values(record_id,field_id,file_name,mime_type,file_size,data,created_at) "
                        + "SELECT record_id,field_id,file_name,mime_type,file_size,data,created_at "
                        + "FROM backup_document_values WHERE snapshot_id=?")) {
            statement.setLong(1, snapshotId);
            statement.executeUpdate();
        }
    }

    private void rollbackQuietly() {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // The original database error is more useful.
        }
        try {
            connection.setAutoCommit(true);
        } catch (SQLException ignored) {
            // The original database error is more useful.
        }
    }

    private void checkpointWal() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA wal_checkpoint(TRUNCATE)")) {
            if (result.next() && result.getInt(1) != 0) {
                throw new SQLException("SQLite could not obtain the lock needed to checkpoint the WAL.");
            }
        }
    }

    @Override
    public void close() {
        SQLException failure = null;
        try {
            checkpointWal();
        } catch (SQLException exception) {
            failure = exception;
        }
        try {
            connection.close();
        } catch (SQLException exception) {
            if (failure == null) {
                failure = exception;
            } else {
                failure.addSuppressed(exception);
            }
        }
        if (failure != null) {
            throw new DatabaseException("Could not close embedded document storage cleanly or checkpoint pending SQLite data.",
                    failure);
        }
    }
}
