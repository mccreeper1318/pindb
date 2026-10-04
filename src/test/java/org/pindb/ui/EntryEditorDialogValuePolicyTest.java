package org.pindb.ui;

import org.junit.jupiter.api.Test;
import org.pindb.model.FieldDefinition;
import org.pindb.model.FieldType;
import org.pindb.model.RecordData;
import org.pindb.model.SummaryType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class EntryEditorDialogValuePolicyTest {

    @Test
    void editingUnrelatedFieldPreservesBlankEditorValues() {
        FieldDefinition date = field(1, FieldType.DATE, "${TODAY}", List.of());
        FieldDefinition dateTime = field(2, FieldType.DATE_TIME, "${NOW}", List.of());
        FieldDefinition dropdown = field(3, FieldType.DROPDOWN, "Open", List.of("Open", "Closed"));
        FieldDefinition bool = field(4, FieldType.BOOLEAN, "true", List.of());
        LocalDateTime now = LocalDateTime.now();
        RecordData existing = new RecordData(42, now, now, null,
                Map.of(1L, "", 2L, "", 3L, "", 4L, ""));

        String dateValue = EntryEditorDialog.initialValue(date, existing);
        String dateTimeValue = EntryEditorDialog.initialValue(dateTime, existing);
        String dropdownValue = EntryEditorDialog.initialValue(dropdown, existing);
        String booleanValue = EntryEditorDialog.initialValue(bool, existing);

        assertEquals("", dateValue);
        assertNull(UiUtil.parseDate(dateValue));
        assertEquals("", dateTimeValue);
        assertNull(EntryEditorDialog.parseDateTime(dateTimeValue));
        assertEquals("", dropdownValue);
        assertNull(EntryEditorDialog.initialDropdownValue(dropdown, dropdownValue));
        assertEquals("", booleanValue);
        assertNull(EntryEditorDialog.parseBoolean(booleanValue));
        assertEquals("", EntryEditorDialog.serializeBoolean(true, false));
    }

    @Test
    void creatingEntryStillAppliesConfiguredDefaults() {
        FieldDefinition date = field(1, FieldType.DATE, "${TODAY}", List.of());
        FieldDefinition dateTime = field(2, FieldType.DATE_TIME, "${NOW}", List.of());
        FieldDefinition dropdown = field(3, FieldType.DROPDOWN, "Open", List.of("Open", "Closed"));
        FieldDefinition bool = field(4, FieldType.BOOLEAN, "true", List.of());

        String dateValue = EntryEditorDialog.initialValue(date, null);
        String dateTimeValue = EntryEditorDialog.initialValue(dateTime, null);
        String dropdownValue = EntryEditorDialog.initialValue(dropdown, null);
        String booleanValue = EntryEditorDialog.initialValue(bool, null);

        assertFalse(dateValue.isBlank());
        assertNotNull(UiUtil.parseDate(dateValue));
        assertFalse(dateTimeValue.isBlank());
        assertNotNull(EntryEditorDialog.parseDateTime(dateTimeValue));
        assertEquals("Open", EntryEditorDialog.initialDropdownValue(dropdown, dropdownValue));
        assertEquals(Boolean.TRUE, EntryEditorDialog.parseBoolean(booleanValue));
    }

    private static FieldDefinition field(long id, FieldType type, String defaultValue, List<String> options) {
        return new FieldDefinition(id, type.displayName(), type, (int) id, false,
                defaultValue, "", "", false, null, options, SummaryType.NONE);
    }
}
