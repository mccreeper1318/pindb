package org.pindb.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pindb.model.DatabaseView;
import org.pindb.model.FieldDefinition;
import org.pindb.model.FieldType;
import org.pindb.model.FilterSpec;
import org.pindb.model.SavedView;
import org.pindb.model.SummaryType;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SavedViewsPersistenceTest {
    @TempDir
    Path tempDirectory;

    @Test
    void createsUpdatesPersistsAndDeletesSavedViews() {
        Path file = tempDirectory.resolve("saved-views.pindb");
        FieldDefinition date = field("Purchased", FieldType.DATE, 0);
        FieldDefinition quantity = field("Quantity", FieldType.NUMBER, 1);
        FieldDefinition category = field("Category", FieldType.DROPDOWN, 2);
        category.setDropdownOptions(List.of("General", "Tools"));

        String savedId;
        try (DatabaseService database = DatabaseService.create(file, "Saved Views", "",
                List.of(date, quantity, category), DatabaseView.TABLE, 10)) {
            List<FieldDefinition> fields = database.fields();
            FilterSpec filter = new FilterSpec(
                    fields.get(0).id(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                    fields.get(1).id(), BigDecimal.ZERO, BigDecimal.TEN,
                    fields.get(2).id(), "Tools");

            SavedView created = database.createSavedView(
                    "Tools This Year", "wrench", filter,
                    List.of(new SavedView.SortSpec(fields.get(1).id(), false)));
            savedId = created.id();

            assertEquals(1, database.savedViews().size());
            assertEquals(created, database.savedViews().getFirst());

            database.addRecord(Map.of(
                    fields.get(0).id(), "2026-06-01",
                    fields.get(1).id(), "5",
                    fields.get(2).id(), "Tools"));
            database.addRecord(Map.of(
                    fields.get(0).id(), "2026-06-01",
                    fields.get(1).id(), "20",
                    fields.get(2).id(), "Tools"));
            Map<Long, FieldDefinition> fieldsById = Map.of(
                    fields.get(0).id(), fields.get(0),
                    fields.get(1).id(), fields.get(1),
                    fields.get(2).id(), fields.get(2));
            assertEquals(1, database.activeRecords().stream()
                    .filter(record -> created.filterSpec().matches(record, fieldsById))
                    .count());

            SavedView updated = new SavedView(created.id(), "Low Stock Tools", "", filter,
                    List.of(new SavedView.SortSpec(fields.get(1).id(), true)));
            database.updateSavedView(updated);
            assertEquals(updated, database.savedViews().getFirst());

            assertThrows(DatabaseException.class, () -> database.createSavedView(
                    "low stock tools", "", FilterSpec.empty(), List.of()));
        }

        try (DatabaseService reopened = DatabaseService.open(file)) {
            SavedView persisted = reopened.savedViews().getFirst();
            assertEquals(savedId, persisted.id());
            assertEquals("Low Stock Tools", persisted.name());
            assertEquals(BigDecimal.TEN, persisted.filterSpec().numericMaximum());
            assertTrue(persisted.sortOrder().getFirst().ascending());

            reopened.deleteSavedView(savedId);
            assertTrue(reopened.savedViews().isEmpty());
        }
    }

    @Test
    void removedOrChangedFieldsDoNotCorruptSavedViews() {
        Path file = tempDirectory.resolve("stale-saved-view.pindb");
        FieldDefinition quantity = field("Quantity", FieldType.NUMBER, 0);

        try (DatabaseService database = DatabaseService.create(file, "Stale View", "",
                List.of(quantity), DatabaseView.TABLE, 10)) {
            FieldDefinition stored = database.fields().getFirst();
            SavedView saved = database.createSavedView("Low Stock", "",
                    new FilterSpec(null, null, null, stored.id(), BigDecimal.ZERO, BigDecimal.TEN,
                            null, ""),
                    List.of(new SavedView.SortSpec(stored.id(), true)));

            FieldDefinition changed = stored.copy();
            changed.setType(FieldType.TEXT);
            database.updateField(changed);
            Map<Long, FieldDefinition> changedFields = Map.of(changed.id(), database.fields().getFirst());
            assertFalse(saved.validationProblems(changedFields).isEmpty());

            database.deleteField(stored.id());

            SavedView stillStored = database.savedViews().getFirst();
            assertEquals(saved.id(), stillStored.id());
            List<String> problems = stillStored.validationProblems(Map.of());
            assertFalse(problems.isEmpty());
            assertTrue(database.integrityCheck());
        }
    }

    @Test
    void logicalBackupsIncludeSavedViewMetadata() {
        Path file = tempDirectory.resolve("saved-view-backup.pindb");
        try (DatabaseService database = DatabaseService.create(file, "Backup", "",
                List.of(field("Name", FieldType.TEXT, 0)), DatabaseView.TABLE, 3)) {
            database.createSavedView("Search View", "example", FilterSpec.empty(), List.of());
            database.createSnapshot("With saved view");
            long withView = database.backupSnapshots().getFirst().id();

            database.deleteSavedView(database.savedViews().getFirst().id());
            assertTrue(database.savedViews().isEmpty());

            database.restoreSnapshot(withView);
            assertEquals("Search View", database.savedViews().getFirst().name());
        }
    }

    private static FieldDefinition field(String name, FieldType type, int position) {
        return new FieldDefinition(0, name, type, position, false, "", "", "", false,
                null, List.of(), SummaryType.NONE);
    }
}
