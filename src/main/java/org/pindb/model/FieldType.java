package org.pindb.model;

public enum FieldType {
    TEXT("Text"),
    MULTILINE_TEXT("Multiline Text"),
    NUMBER("Number"),
    CURRENCY("Currency"),
    DATE("Date"),
    DATE_TIME("Date and Time"),
    BOOLEAN("Yes / No"),
    DROPDOWN("Dropdown List");

    private final String displayName;

    FieldType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public boolean isNumeric() {
        return this == NUMBER || this == CURRENCY;
    }

    public boolean supportsSummary() {
        return isNumeric() || this == TEXT || this == MULTILINE_TEXT || this == BOOLEAN || this == DROPDOWN;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
