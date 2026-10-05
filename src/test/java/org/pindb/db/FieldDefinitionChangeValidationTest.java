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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FieldDefinitionChangeValidationTest {
    @TempDir
    Path tempDirectory;

    @Test
    void requiredConstraintChecksRecentlyDeletedEntriesAndDoesNotCreateBackupOnFailure() {
        Path file = tempDirectory.resolve("required-change.pindb");
        try (DatabaseService database = DatabaseService.create(file, "Required", "",
                List.of(field("Name", FieldType.TEXT, 0)), DatabaseView.TABLE, 10)) {
            long fieldId = database.fields().getFirst().id();
            long deletedId = database.addRecord(Map.of(fieldId, ""));
            database.moveToTrash(deletedId);
            database.addRecord(Map.of(fieldId, "Present"));
            int snapshotCount = database.backupSnapshots().size();

            FieldDefinition updated = database.fields().getFirst().copy();
            updated.setRequired(true);

            DatabaseException failure = assertThrows(DatabaseException.class, () -> database.updateField(updated));
            assertTrue(failure.getMessage().contains("Recently Deleted"));
            assertFalse(database.fields().getFirst().required());
            assertEquals(snapshotCount, database.backupSnapshots().size());
        }
    }

    @Test
    void uniqueConstraintChecksDuplicatesAcrossActiveAndDeletedEntries() {
        Path file = tempDirectory.resolve("unique-change.pindb");
        try (DatabaseService database = DatabaseService.create(file, "Unique", "",
                List.of(field("Name", FieldType.TEXT, 0)), DatabaseView.TABLE, 10)) {
            long fieldId = database.fields().getFirst().id();
            database.addRecord(Map.of(fieldId, "Alex"));
            long deletedId = database.addRecord(Map.of(fieldId, "alex"));
            database.moveToTrash(deletedId);

            FieldDefinition updated = database.fields().getFirst().copy();
            updated.setUniqueValue(true);

            DatabaseException failure = assertThrows(DatabaseException.class, () -> database.updateField(updated));
            assertTrue(failure.getMessage().contains("not unique"));
            assertFalse(database.fields().getFirst().uniqueValue());
        }
    }

    @Test
    void typeChangePreflightsExistingValuesIncludingDeletedEntries() {
        Path file = tempDirectory.resolve("type-change.pindb");
        try (DatabaseService database = DatabaseService.create(file, "Types", "",
                List.of(field("Amount", FieldType.TEXT, 0)), DatabaseView.TABLE, 10)) {
            long fieldId = database.fields().getFirst().id();
            database.addRecord(Map.of(fieldId, "12.5"));
            long deletedId = database.addRecord(Map.of(fieldId, "not-a-number"));
            database.moveToTrash(deletedId);

            FieldDefinition updated = database.fields().getFirst().copy();
            updated.setType(FieldType.NUMBER);
            updated.setMinValue("0");
            updated.setMaxValue("20");

            DatabaseException failure = assertThrows(DatabaseException.class, () -> database.updateField(updated));
            assertTrue(failure.getMessage().contains("Recently Deleted"));
            assertEquals(FieldType.TEXT, database.fields().getFirst().type());
        }
    }

    @Test
    void safeTypeChangeSucceedsWhenEveryStoredValueMatchesNewRules() {
        Path file = tempDirectory.resolve("safe-type-change.pindb");
        try (DatabaseService database = DatabaseService.create(file, "Safe Types", "",
                List.of(field("Amount", FieldType.TEXT, 0)), DatabaseView.TABLE, 10)) {
            long fieldId = database.fields().getFirst().id();
            database.addRecord(Map.of(fieldId, "12.5"));
            long deletedId = database.addRecord(Map.of(fieldId, "8"));
            database.moveToTrash(deletedId);

            FieldDefinition updated = database.fields().getFirst().copy();
            updated.setType(FieldType.NUMBER);
            updated.setMinValue("0");
            updated.setMaxValue("20");
            database.updateField(updated);

            FieldDefinition stored = database.fields().getFirst();
            assertEquals(FieldType.NUMBER, stored.type());
            assertEquals("0", stored.minValue());
            assertEquals("20", stored.maxValue());
        }
    }

    @Test
    void dropdownOptionRemovalCannotInvalidateDeletedData() {
        Path file = tempDirectory.resolve("dropdown-change.pindb");
        FieldDefinition status = field("Status", FieldType.DROPDOWN, 0);
        status.setDropdownOptions(List.of("Open", "Closed"));
        try (DatabaseService database = DatabaseService.create(file, "Dropdown", "",
                List.of(status), DatabaseView.TABLE, 10)) {
            long fieldId = database.fields().getFirst().id();
            long deletedId = database.addRecord(Map.of(fieldId, "Closed"));
            database.moveToTrash(deletedId);

            FieldDefinition updated = database.fields().getFirst().copy();
            updated.setDropdownOptions(List.of("Open"));

            assertThrows(DatabaseException.class, () -> database.updateField(updated));
            assertEquals(List.of("Open", "Closed"), database.fields().getFirst().dropdownOptions());
        }
    }

    @Test
    void rejectsInvalidBoundsAndDefaultBeforeMetadataChanges() {
        Path file = tempDirectory.resolve("definition-validation.pindb");
        try (DatabaseService database = DatabaseService.create(file, "Definitions", "",
                List.of(field("Amount", FieldType.NUMBER, 0)), DatabaseView.TABLE, 10)) {
            FieldDefinition invalidBounds = database.fields().getFirst().copy();
            invalidBounds.setMinValue("10");
            invalidBounds.setMaxValue("5");
            assertThrows(DatabaseException.class, () -> database.updateField(invalidBounds));

            FieldDefinition invalidDefault = database.fields().getFirst().copy();
            invalidDefault.setMinValue("0");
            invalidDefault.setMaxValue("20");
            invalidDefault.setDefaultValue("25");
            DatabaseException failure = assertThrows(DatabaseException.class,
                    () -> database.updateField(invalidDefault));
            assertTrue(failure.getMessage().contains("Default value"));

            FieldDefinition stored = database.fields().getFirst();
            assertTrue(stored.minValue().isBlank());
            assertTrue(stored.maxValue().isBlank());
            assertTrue(stored.defaultValue().isBlank());
        }
    }

    @Test
    void addingRequiredFieldIsBlockedWhenEntriesAlreadyExist() {
        Path file = tempDirectory.resolve("required-add.pindb");
        try (DatabaseService database = DatabaseService.create(file, "Required Add", "",
                List.of(field("Existing", FieldType.TEXT, 0)), DatabaseView.TABLE, 10)) {
            long fieldId = database.fields().getFirst().id();
            database.addRecord(Map.of(fieldId, "value"));

            FieldDefinition required = field("New Required", FieldType.TEXT, 1);
            required.setRequired(true);
            assertThrows(DatabaseException.class, () -> database.addField(required));
            assertEquals(1, database.fields().size());
        }
    }

    @Test
    void documentTypeChangesCannotOrphanBlobsOrCreateFilenameOnlyDocuments() {
        Path file = tempDirectory.resolve("document-change.pindb");
        FieldDefinition attachment = field("Attachment", FieldType.DOCUMENT, 0);
        FieldDefinition notes = field("Notes", FieldType.TEXT, 1);
        try (DatabaseService database = DatabaseService.create(file, "Documents", "",
                List.of(attachment, notes), DatabaseView.TABLE, 10);
             DocumentStore documents = new DocumentStore(file)) {
            List<FieldDefinition> fields = database.fields();
            long documentFieldId = fields.get(0).id();
            long textFieldId = fields.get(1).id();
            long recordId = database.addRecord(Map.of(
                    documentFieldId, "report.txt",
                    textFieldId, "plain text"));
            documents.replaceDocuments(recordId, Map.of(documentFieldId,
                    new DocumentData("report.txt", "text/plain",
                            "report".getBytes(StandardCharsets.UTF_8))));

            FieldDefinition documentToText = database.fields().get(0).copy();
            documentToText.setType(FieldType.TEXT);
            DatabaseException blobFailure = assertThrows(DatabaseException.class,
                    () -> database.updateField(documentToText));
            assertTrue(blobFailure.getMessage().contains("embedded file"));
            assertEquals(FieldType.DOCUMENT, database.fields().get(0).type());
            assertTrue(documents.document(recordId, documentFieldId).isPresent());

            FieldDefinition textToDocument = database.fields().get(1).copy();
            textToDocument.setType(FieldType.DOCUMENT);
            textToDocument.setDropdownOptions(List.of());
            DatabaseException filenameFailure = assertThrows(DatabaseException.class,
                    () -> database.updateField(textToDocument));
            assertTrue(filenameFailure.getMessage().contains("Clear this field"));
            assertEquals(FieldType.TEXT, database.fields().get(1).type());
        }
    }

    private static FieldDefinition field(String name, FieldType type, int position) {
        return new FieldDefinition(0, name, type, position, false, "", "", "", false,
                null, List.of(), SummaryType.NONE);
    }
}
