package org.pindb.db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

public final class DatabaseMigrator {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    private static final DateTimeFormatter BACKUP_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final byte[] SQLITE_HEADER = "SQLite format 3\0".getBytes(StandardCharsets.US_ASCII);

    private DatabaseMigrator() {
    }

    public static void ensureCompatible(Path path) {
        if (!Files.isRegularFile(path)) {
            throw new DatabaseException("Database file does not exist: " + path);
        }

        try {
            if (Files.size(path) == 0) {
                throw new DatabaseException("This database file is empty (0 bytes). It may have been incompletely copied "
                        + "or damaged. Recopy the original .pindb file or restore a known-good backup.");
            }
            if (!hasSqliteHeader(path)) {
                throw new DatabaseException("This .pindb file is not an SQLite database. It may be the wrong file, an "
                        + "incomplete transfer, or a damaged copy. Recopy the original file or restore a backup.");
            }
        } catch (IOException exception) {
            throw new DatabaseException("PinDB could not inspect this database file before opening it.", exception);
        }

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + path.toAbsolutePath())) {
            verifyIntegrity(connection);

            if (!tableExists(connection, "pindb_meta")) {
                throw new DatabaseException("This is an SQLite database, but it is missing PinDB metadata. It may not "
                        + "be a PinDB database, or it may have been incompletely copied or damaged. Recopy the original "
                        + ".pindb file or restore a backup.");
            }

            String schemaVersion = readSchemaVersion(connection);
            if (schemaVersion == null || schemaVersion.isBlank()) {
                throw new DatabaseException("This database is missing its PinDB schema version metadata. It may be "
                        + "incomplete or damaged. Recopy the original .pindb file or restore a backup.");
            }

            int version;
            try {
                version = Integer.parseInt(schemaVersion);
            } catch (NumberFormatException exception) {
                throw new DatabaseException("This database contains invalid PinDB schema metadata and may be damaged. "
                        + "Restore a known-good backup before making further changes.", exception);
            }

            if (version > CURRENT_SCHEMA_VERSION) {
                throw new DatabaseException("This database was created by a newer version of PinDB. "
                        + "Database schema " + version + " is newer than supported schema " + CURRENT_SCHEMA_VERSION + ".");
            }
            if (version < CURRENT_SCHEMA_VERSION) {
                Path untouchedBackup = migrationBackupPath(path);
                Files.copy(path, untouchedBackup, StandardCopyOption.COPY_ATTRIBUTES);
                migrate(connection, version);
            }
        } catch (DatabaseException exception) {
            throw exception;
        } catch (SQLException exception) {
            throw new DatabaseException("SQLite could not read this database correctly. The file may be corrupted or "
                    + "incompletely transferred. Restore a known-good backup or recopy the original .pindb file.", exception);
        } catch (IOException exception) {
            throw new DatabaseException("Could not create a safety copy before migrating this database.", exception);
        }
    }

    private static boolean hasSqliteHeader(Path path) throws IOException {
        byte[] header;
        try (InputStream input = Files.newInputStream(path)) {
            header = input.readNBytes(SQLITE_HEADER.length);
        }
        return Arrays.equals(header, SQLITE_HEADER);
    }

    private static void verifyIntegrity(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA quick_check(1)")) {
            if (!result.next() || !"ok".equalsIgnoreCase(result.getString(1))) {
                String detail = result.isClosed() ? "unknown SQLite integrity error" : result.getString(1);
                throw new DatabaseException("SQLite reports that this database is damaged or corrupted"
                        + (detail == null || detail.isBlank() ? "." : ": " + detail)
                        + " Restore a known-good backup before making further changes.");
            }
        }
    }

    private static boolean tableExists(Connection connection, String tableName) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?")) {
            statement.setString(1, tableName);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private static String readSchemaVersion(Connection connection) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT value FROM pindb_meta WHERE key='schema_version'")) {
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getString(1) : null;
            }
        }
    }

    private static void migrate(Connection connection, int version) throws SQLException {
        connection.setAutoCommit(false);
        try {
            int current = version;
            while (current < CURRENT_SCHEMA_VERSION) {
                current++;
                // Future migrations are applied here, one schema version at a time.
            }
            try (var statement = connection.prepareStatement(
                    "INSERT INTO pindb_meta(key,value) VALUES('schema_version',?) "
                            + "ON CONFLICT(key) DO UPDATE SET value=excluded.value")) {
                statement.setString(1, String.valueOf(CURRENT_SCHEMA_VERSION));
                statement.executeUpdate();
            }
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private static Path migrationBackupPath(Path original) {
        String fileName = original.getFileName().toString();
        String stem = fileName.toLowerCase().endsWith(".pindb")
                ? fileName.substring(0, fileName.length() - 6)
                : fileName;
        String backupName = stem + ".pre-migration-" + BACKUP_STAMP.format(LocalDateTime.now()) + ".pindb";
        return original.resolveSibling(backupName);
    }
}
