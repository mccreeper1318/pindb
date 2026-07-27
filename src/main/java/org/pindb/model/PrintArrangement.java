package org.pindb.model;

public enum PrintArrangement {
    COLUMNS("Fields as Columns"),
    ROWS("Fields as Rows");

    private final String displayName;

    PrintArrangement(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
