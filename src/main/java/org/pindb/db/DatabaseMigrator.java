package org.pindb.db;

import java.io.IOException;
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

public final class DatabaseMigrator {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    private static final DateTimeFormatter BACKUP_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private DatabaseMigrator() {
    }

    public static void ensureCompatible(Path path) {
        if (!Files.isRegularFile(path)) {
            throw new DatabaseException("Database file does not exist: " + path);
        }
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + path.toAbsolutePath())) {
            int version = readSchemaVersion(connection);
            if (version == 0) {
                throw new DatabaseException("This file is not a valid PinDB database: " + path.getFileName());
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
        } catch (SQLException | IOException exception) {
            throw new DatabaseException("Could not prepare database for this PinDB version.", exception);
        }
    }

    private static int readSchemaVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            try (ResultSet tables = statement.executeQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='pindb_meta'")) {
                if (!tables.next()) {
                    return 0;
                }
            }
            try (ResultSet result = statement.executeQuery(
                    "SELECT value FROM pindb_meta WHERE key='schema_version'")) {
                if (!result.next()) {
                    return 0;
                }
                return Integer.parseInt(result.getString(1));
            }
        } catch (NumberFormatException exception) {
            throw new SQLException("Invalid schema version metadata.", exception);
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
