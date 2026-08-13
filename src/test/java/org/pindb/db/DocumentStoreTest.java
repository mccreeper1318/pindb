package org.pindb.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pindb.model.DatabaseView;
import org.pindb.model.DocumentData;
import org.pindb.model.FieldDefinition;
import org.pindb.model.FieldType;
import org.pindb.model.SummaryType;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentStoreTest {
    @TempDir
    Path tempDirectory;

    @Test
    void storesDocumentsInsideDatabaseAndRestoresThemFromSnapshots() {
        Path databasePath = tempDirectory.resolve("documents.pindb");
        FieldDefinition documentField = new FieldDefinition(0, "Document", FieldType.DOCUMENT, 0,
                false, "", "", "", false, null, List.of(), SummaryType.NONE);

        try (DatabaseService database = DatabaseService.create(databasePath, "Documents", "",
                List.of(documentField), DatabaseView.TABLE, 10);
             DocumentStore documents = new DocumentStore(databasePath)) {
            FieldDefinition createdField = database.fields().getFirst();
            long recordId = database.addRecord(Map.of(createdField.id(), "first.txt"));
            DocumentData first = new DocumentData("first.txt", "text/plain",
                    "first version".getBytes(StandardCharsets.UTF_8));
            documents.replaceDocuments(recordId, Map.of(createdField.id(), first));

            DocumentData loaded = documents.document(recordId, createdField.id()).orElseThrow();
            assertEquals("first.txt", loaded.fileName());
            assertArrayEquals(first.data(), loaded.data());

            database.createSnapshot("Document snapshot");
            long snapshotId = database.backupSnapshots().getFirst().id();

            DocumentData replacement = new DocumentData("second.txt", "text/plain",
                    "second version".getBytes(StandardCharsets.UTF_8));
            documents.replaceDocuments(recordId, Map.of(createdField.id(), replacement));
            documents.restoreSnapshot(snapshotId);

            DocumentData restored = documents.document(recordId, createdField.id()).orElseThrow();
            assertEquals("first.txt", restored.fileName());
            assertArrayEquals(first.data(), restored.data());
            assertTrue(database.integrityCheck());
        }
    }
}
