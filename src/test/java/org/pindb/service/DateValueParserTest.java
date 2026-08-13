package org.pindb.service;

import org.junit.jupiter.api.Test;
import org.pindb.model.FieldType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DateValueParserTest {
    @Test
    void infersAndNormalizesShortUsDates() {
        List<String> values = List.of("12/08/25", "1/5/2026", "");

        assertEquals(FieldType.DATE, DateValueParser.inferType(values));
        assertEquals("2025-12-08", DateValueParser.normalize(FieldType.DATE, "12/08/25"));
        assertEquals("2026-01-05", DateValueParser.normalize(FieldType.DATE, "1/5/2026"));
    }

    @Test
    void infersAndNormalizesDateTimes() {
        List<String> values = List.of("12/08/25 2:30 PM", "1/5/2026 14:45");

        assertEquals(FieldType.DATE_TIME, DateValueParser.inferType(values));
        assertEquals("2025-12-08T14:30",
                DateValueParser.normalize(FieldType.DATE_TIME, "12/08/25 2:30 PM"));
    }

    @Test
    void leavesMixedTextColumnsAsText() {
        assertEquals(FieldType.TEXT, DateValueParser.inferType(List.of("12/08/25", "Not a date")));
        assertEquals("12/08/25", DateValueParser.normalize(FieldType.TEXT, "12/08/25"));
    }
}
