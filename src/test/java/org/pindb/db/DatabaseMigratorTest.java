package org.pindb.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void preMigrationBackupIncludesCommittedWalData() throws Exception {
        Class.forName("org.sqlite.JDBC");
        Path database = tempDirectory.resolve("legacy.pindb");
        createLegacyDatabase(database);

        Path wal = Path.of(database + "-wal");
        try (Connection writer = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             Statement statement = writer.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA wal_autocheckpoint=0");
            statement.executeUpdate("INSERT INTO migration_probe(value) VALUES('committed-only-in-wal')");

            assertTrue(Files.isRegularFile(wal));
            assertTrue(Files.size(wal) > 0);

            Path mainOnly = tempDirectory.resolve("main-only-before-migration.pindb");
            Files.copy(database, mainOnly);
            assertEquals(0, probeCount(mainOnly),
                    "The fixture must leave the committed probe row outside the main database file.");

            DatabaseMigrator.ensureCompatible(database);
        }

        List<Path> backups;
        try (var files = Files.list(tempDirectory)) {
            backups = files
                    .filter(candidate -> candidate.getFileName().toString().startsWith("legacy.pre-migration-"))
                    .filter(candidate -> candidate.getFileName().toString().endsWith(".pindb"))
                    .toList();
        }

        assertEquals(1, backups.size());
        Path backup = backups.getFirst();
        assertEquals(1, probeCount(backup));
        assertEquals("0", schemaVersion(backup),
                "The safety copy must remain the untouched pre-migration schema.");
        assertEquals("1", schemaVersion(database));
        assertEquals(1, probeCount(database));
    }

    private static void createLegacyDatabase(Path database) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA wal_autocheckpoint=0");
            statement.execute("CREATE TABLE pindb_meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
            statement.executeUpdate("INSERT INTO pindb_meta(key,value) VALUES('schema_version','0')");
            statement.execute("CREATE TABLE migration_probe (value TEXT NOT NULL)");
            try (ResultSet result = statement.executeQuery("PRAGMA wal_checkpoint(TRUNCATE)")) {
                assertTrue(result.next());
                assertEquals(0, result.getInt(1));
            }
        }
    }

    private static int probeCount(Path database) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM migration_probe")) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }

    private static String schemaVersion(Path database) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT value FROM pindb_meta WHERE key='schema_version'")) {
            assertTrue(result.next());
            return result.getString(1);
        }
    }
}
