package org.pindb.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pindb.model.DatabaseView;
import org.pindb.model.FieldDefinition;
import org.pindb.model.FieldType;
import org.pindb.model.SummaryType;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseServiceTest {
    @TempDir
    Path tempDirectory;

    @Test
    void createsPortableSqliteDatabaseAndPersistsRecords() {
        Path file = tempDirectory.resolve("payments.pindb");
        FieldDefinition date = field("Date", FieldType.DATE, 0);
        date.setRequired(true);
        FieldDefinition amount = field("Amount", FieldType.CURRENCY, 1);
        amount.setRequired(true);
        amount.setSummaryType(SummaryType.SUM);

        try (DatabaseService database = DatabaseService.create(file, "Payments", "Test database",
                List.of(date, amount), DatabaseView.TABLE, 10)) {
            List<FieldDefinition> fields = database.fields();
            Map<Long, String> values = new LinkedHashMap<>();
            values.put(fields.get(0).id(), "2026-07-27");
            values.put(fields.get(1).id(), "50");
            database.addRecord(values);
            assertEquals(1, database.countActiveRecords());
            assertEquals("$50.00", database.summaries().values().iterator().next());
            assertTrue(database.integrityCheck());
        }

        try (DatabaseService reopened = DatabaseService.open(file)) {
            assertEquals("Payments", reopened.info().name());
            assertEquals("50", reopened.activeRecords().getFirst().value(reopened.fields().get(1).id()));
        }
    }

    @Test
    void validatesUniqueAndRequiredFields() {
        Path file = tempDirectory.resolve("contacts.pindb");
        FieldDefinition name = field("Name", FieldType.TEXT, 0);
        name.setRequired(true);
        name.setUniqueValue(true);
        try (DatabaseService database = DatabaseService.create(file, "Contacts", "",
                List.of(name), DatabaseView.TABLE, 10)) {
            long id = database.fields().getFirst().id();
            database.addRecord(Map.of(id, "Alex"));
            assertThrows(DatabaseException.class, () -> database.addRecord(Map.of(id, "Alex")));
            assertThrows(DatabaseException.class, () -> database.addRecord(Map.of(id, "")));
        }
    }

    @Test
    void trashAndSnapshotsAreRecoverable() {
        Path file = tempDirectory.resolve("trash.pindb");
        try (DatabaseService database = DatabaseService.create(file, "Trash", "",
                List.of(field("Name", FieldType.TEXT, 0)), DatabaseView.TABLE, 10)) {
            long fieldId = database.fields().getFirst().id();
            long recordId = database.addRecord(Map.of(fieldId, "Recover me"));
            database.moveToTrash(recordId);
            assertEquals(0, database.activeRecords().size());
            assertEquals(1, database.deletedRecords().size());
            database.restoreRecord(recordId);
            assertEquals(1, database.activeRecords().size());
            assertFalse(database.backupSnapshots().isEmpty());
        }
    }

    private static FieldDefinition field(String name, FieldType type, int position) {
        return new FieldDefinition(0, name, type, position, false, "", "", "", false,
                null, List.of(), SummaryType.NONE);
    }
}
