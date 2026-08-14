package org.pindb.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pindb.model.DatabaseView;
import org.pindb.model.DocumentData;
import org.pindb.model.FieldDefinition;
import org.pindb.model.FieldType;
import org.pindb.model.SummaryType;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentStoreTest {
    @TempDir
    Path tempDirectory;

    @Test
    void storesDocumentsInsideDatabaseAndRestoresThemFromSnapshots() {
        Path databasePath = tempDirectory.resolve("documents.pindb");
        FieldDefinition documentField = documentField();

        try (DatabaseService database = DatabaseService.create(databasePath, "Documents", "",
                List.of(documentField), DatabaseView.TABLE, 10);
             DocumentStore documents = new DocumentStore(databasePath)) {
            FieldDefinition createdField = database.fields().getFirst();
            long recordId = database.addRecord(Map.of(createdField.id(), "first.txt"));
            DocumentData first = textDocument("first.txt", "first version");
            documents.replaceDocuments(recordId, Map.of(createdField.id(), first));

            DocumentData loaded = documents.document(recordId, createdField.id()).orElseThrow();
            assertEquals("first.txt", loaded.fileName());
            assertArrayEquals(first.data(), loaded.data());

            database.createSnapshot("Document snapshot");
            long snapshotId = database.backupSnapshots().getFirst().id();

            DocumentData replacement = textDocument("second.txt", "second version");
            documents.replaceDocuments(recordId, Map.of(createdField.id(), replacement));
            documents.restoreSnapshot(snapshotId);

            DocumentData restored = documents.document(recordId, createdField.id()).orElseThrow();
            assertEquals("first.txt", restored.fileName());
            assertArrayEquals(first.data(), restored.data());
            assertTrue(database.integrityCheck());
        }
    }

    @Test
    void failedDocumentInsertRollsBackNewRecordAndFilename() {
        Path databasePath = tempDirectory.resolve("atomic-add.pindb");
        try (DatabaseService database = DatabaseService.create(databasePath, "Atomic Add", "",
                List.of(documentField()), DatabaseView.TABLE, 10);
             DocumentStore documents = new DocumentStore(databasePath)) {
            long fieldId = database.fields().getFirst().id();
            long recordId = database.addRecord(Map.of(fieldId, "new.txt"));

            DatabaseException failure = assertThrows(DatabaseException.class,
                    () -> documents.replaceDocuments(recordId,
                            Map.of(Long.MAX_VALUE, textDocument("new.txt", "new data"))));

            assertTrue(failure.getMessage().contains("rolled back"));
            assertEquals(0, database.countActiveRecords());
        }
    }

    @Test
    void failedDocumentUpdateRestoresPreviousRecordAndBlob() {
        Path databasePath = tempDirectory.resolve("atomic-edit.pindb");
        try (DatabaseService database = DatabaseService.create(databasePath, "Atomic Edit", "",
                List.of(documentField()), DatabaseView.TABLE, 10);
             DocumentStore documents = new DocumentStore(databasePath)) {
            long fieldId = database.fields().getFirst().id();
            long recordId = database.addRecord(Map.of(fieldId, "old.txt"));
            DocumentData oldDocument = textDocument("old.txt", "old data");
            documents.replaceDocuments(recordId, Map.of(fieldId, oldDocument));

            database.updateRecord(recordId, Map.of(fieldId, "new.txt"));
            assertThrows(DatabaseException.class,
                    () -> documents.replaceDocuments(recordId,
                            Map.of(Long.MAX_VALUE, textDocument("new.txt", "new data"))));

            assertEquals("old.txt", database.activeRecords().getFirst().value(fieldId));
            DocumentData restored = documents.document(recordId, fieldId).orElseThrow();
            assertEquals("old.txt", restored.fileName());
            assertArrayEquals(oldDocument.data(), restored.data());
        }
    }

    @Test
    void normalCloseLeavesMainDatabaseSelfContainedForCopying() throws Exception {
        Path databasePath = tempDirectory.resolve("portable.pindb");
        long fieldId;
        long recordId;

        try (DatabaseService database = DatabaseService.create(databasePath, "Portable", "",
                List.of(documentField()), DatabaseView.TABLE, 10);
             DocumentStore documents = new DocumentStore(databasePath)) {
            fieldId = database.fields().getFirst().id();
            recordId = database.addRecord(Map.of(fieldId, "portable.txt"));
            documents.replaceDocuments(recordId,
                    Map.of(fieldId, textDocument("portable.txt", "stored in the database")));
        }

        Path copied = tempDirectory.resolve("portable-copy.pindb");
        Files.copy(databasePath, copied, StandardCopyOption.COPY_ATTRIBUTES);

        try (DatabaseService database = DatabaseService.open(copied);
             DocumentStore documents = new DocumentStore(copied)) {
            assertEquals(1, database.countActiveRecords());
            DocumentData copiedDocument = documents.document(recordId, fieldId).orElseThrow();
            assertEquals("stored in the database", new String(copiedDocument.data(), StandardCharsets.UTF_8));
            assertTrue(database.integrityCheck());
        }
    }

    private static FieldDefinition documentField() {
        return new FieldDefinition(0, "Document", FieldType.DOCUMENT, 0,
                false, "", "", "", false, null, List.of(), SummaryType.NONE);
    }

    private static DocumentData textDocument(String name, String text) {
        return new DocumentData(name, "text/plain", text.getBytes(StandardCharsets.UTF_8));
    }
}
