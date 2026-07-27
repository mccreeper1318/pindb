package org.pindb.model;

import java.util.List;

public record PrintOptions(
        PrintArrangement arrangement,
        boolean landscape,
        boolean showDatabaseName,
        boolean showPrintDate,
        boolean showPageNumbers,
        boolean repeatHeadings,
        List<Long> fieldIds
) {
    public PrintOptions {
        fieldIds = List.copyOf(fieldIds);
    }
}
