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
        try {
            boolean previousAutoCommit = connection.getAutoCommit();
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
            try {
                connection.rollback();
                connection.setAutoCommit(true);
            } catch (SQLException ignored) {
                // The original database error is more useful.
            }
            throw new DatabaseException("Could not save embedded documents for entry " + recordId + ".", exception);
        }
    }

    public void restoreSnapshot(long snapshotId) {
        try {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
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
            connection.commit();
            connection.setAutoCommit(previousAutoCommit);
        } catch (SQLException exception) {
            try {
                connection.rollback();
                connection.setAutoCommit(true);
            } catch (SQLException ignored) {
                // The original database error is more useful.
            }
            throw new DatabaseException("Could not restore embedded documents from the selected backup.", exception);
        }
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException exception) {
            throw new DatabaseException("Could not close embedded document storage cleanly.", exception);
        }
    }
}
