package org.pindb.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseMigratorTest {
    @TempDir
    Path tempDirectory;

    @Test
    void reportsEmptyDatabaseFileClearly() throws Exception {
        Path file = tempDirectory.resolve("empty.pindb");
        Files.createFile(file);

        DatabaseException exception = assertThrows(DatabaseException.class,
                () -> DatabaseMigrator.ensureCompatible(file));

        assertTrue(exception.getMessage().contains("empty (0 bytes)"));
    }

    @Test
    void reportsNonSqliteFilesClearly() throws Exception {
        Path file = tempDirectory.resolve("not-sqlite.pindb");
        Files.writeString(file, "not a database", StandardCharsets.UTF_8);

        DatabaseException exception = assertThrows(DatabaseException.class,
                () -> DatabaseMigrator.ensureCompatible(file));

        assertTrue(exception.getMessage().contains("not an SQLite database"));
    }

    @Test
    void distinguishesSqliteDatabaseMissingPinDbMetadata() throws Exception {
        Path file = tempDirectory.resolve("other-sqlite.pindb");
        Class.forName("org.sqlite.JDBC");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE unrelated(id INTEGER PRIMARY KEY, value TEXT)");
        }

        DatabaseException exception = assertThrows(DatabaseException.class,
                () -> DatabaseMigrator.ensureCompatible(file));

        assertTrue(exception.getMessage().contains("missing PinDB metadata"));
    }

    @Test
    void distinguishesMissingSchemaVersionMetadata() throws Exception {
        Path file = tempDirectory.resolve("missing-version.pindb");
        Class.forName("org.sqlite.JDBC");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE pindb_meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
            statement.execute("INSERT INTO pindb_meta(key,value) VALUES('database_name','Missing Version')");
        }

        DatabaseException exception = assertThrows(DatabaseException.class,
                () -> DatabaseMigrator.ensureCompatible(file));

        assertTrue(exception.getMessage().contains("schema version metadata"));
    }
}
