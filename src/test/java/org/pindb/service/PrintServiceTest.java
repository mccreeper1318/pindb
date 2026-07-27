package org.pindb.service;

import org.junit.jupiter.api.Test;
import org.pindb.model.FieldDefinition;
import org.pindb.model.FieldType;
import org.pindb.model.RecordData;
import org.pindb.model.SummaryType;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PrintServiceTest {
    @Test
    void summariesUseOnlyTheRecordsBeingPrinted() {
        FieldDefinition amount = new FieldDefinition(1, "Amount", FieldType.CURRENCY, 0,
                false, "", "", "", false, null, List.of(), SummaryType.SUM);
        FieldDefinition note = new FieldDefinition(2, "Note", FieldType.TEXT, 1,
                false, "", "", "", false, null, List.of(), SummaryType.COUNT);

        RecordData first = record(1, Map.of(1L, "50", 2L, "First"));
        RecordData second = record(2, Map.of(1L, "25", 2L, "Second"));

        Map<FieldDefinition, String> summaries = PrintService.summaries(
                List.of(amount, note), List.of(first));

        assertEquals("$50.00", summaries.get(amount));
        assertEquals("1", summaries.get(note));
        assertEquals(2, PrintService.summaries(List.of(amount, note), List.of(first, second)).size());
        assertEquals("$75.00", PrintService.summaries(List.of(amount), List.of(first, second)).get(amount));
    }

    private static RecordData record(long id, Map<Long, String> values) {
        LocalDateTime now = LocalDateTime.of(2026, 7, 27, 12, 0);
        return new RecordData(id, now, now, null, new LinkedHashMap<>(values));
    }
}
