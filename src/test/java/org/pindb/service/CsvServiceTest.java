package org.pindb.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pindb.db.DatabaseException;
import org.pindb.db.DatabaseService;
import org.pindb.model.FieldType;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvServiceTest {
    @TempDir
    Path tempDirectory;

    @Test
    void readsQuotedCsvValues() throws Exception {
        Path csv = tempDirectory.resolve("quoted.csv");
        Files.writeString(csv, "Name,Notes\nAlex,\"Hello, world\"\nSam,\"Line 1\nLine 2\"\n");
        List<List<String>> rows = CsvService.readCsv(csv);
        assertEquals("Hello, world", rows.get(1).get(1));
        assertEquals("Line 1\nLine 2", rows.get(2).get(1));
    }

    @Test
    void importsLargeCsvWithConstantSnapshotCount() throws Exception {
        Path csv = tempDirectory.resolve("large.csv");
        Path destination = tempDirectory.resolve("large.pindb");
        try (BufferedWriter writer = Files.newBufferedWriter(csv)) {
            writer.write("Name,Date\n");
            for (int index = 0; index < 5_000; index++) {
                writer.write("Player " + index + ",10/10/2026\n");
            }
        }

        try (DatabaseService database = CsvService.importCsv(csv, destination, "Large Import")) {
            assertEquals(5_000, database.countActiveRecords());
            assertEquals(FieldType.DATE, database.fields().get(1).type());
            assertEquals(2, database.backupSnapshots().size());
            assertEquals("CSV import completed", database.backupSnapshots().getFirst().reason());
            assertTrue(database.integrityCheck());
        }

        try (var files = Files.list(tempDirectory)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().contains(".import-")));
        }
    }

    @Test
    void rejectsDuplicateHeadersWithoutPublishingDestination() throws Exception {
        Path csv = tempDirectory.resolve("duplicates.csv");
        Path destination = tempDirectory.resolve("duplicates.pindb");
        Files.writeString(csv, "Name, name \nAlex,Other\n");

        DatabaseException failure = assertThrows(DatabaseException.class,
                () -> CsvService.importCsv(csv, destination, "Duplicate Headers"));

        assertTrue(failure.getMessage().contains("duplicate field name"));
        assertFalse(Files.exists(destination));
        try (var files = Files.list(tempDirectory)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().contains(".import-")));
        }
    }

    @Test
    void preservesQuotedNewlinesDuringStreamingImport() throws Exception {
        Path csv = tempDirectory.resolve("streamed-quotes.csv");
        Path destination = tempDirectory.resolve("streamed-quotes.pindb");
        Files.writeString(csv, "Name,Notes\nAlex,\"Line 1\nLine 2\"\n");

        try (DatabaseService database = CsvService.importCsv(csv, destination, "Quoted Import")) {
            long notesField = database.fields().get(1).id();
            assertEquals("Line 1\nLine 2", database.activeRecords().getFirst().value(notesField));
        }
    }
}
