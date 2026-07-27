package org.pindb.model;

public enum SummaryType {
    NONE("None"),
    SUM("Sum"),
    AVERAGE("Average"),
    MINIMUM("Minimum"),
    MAXIMUM("Maximum"),
    COUNT("Entry Count");

    private final String displayName;

    SummaryType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
