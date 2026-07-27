package org.pindb.model;

public enum DatabaseView {
    TABLE("Table View"),
    RECORD("Record View");

    private final String displayName;

    DatabaseView(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
