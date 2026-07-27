package org.pindb.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class FieldDefinition {
    private long id;
    private String name;
    private FieldType type;
    private int position;
    private boolean required;
    private String defaultValue;
    private String minValue;
    private String maxValue;
    private boolean uniqueValue;
    private Integer characterLimit;
    private List<String> dropdownOptions;
    private SummaryType summaryType;

    public FieldDefinition() {
        this(0, "New Field", FieldType.TEXT, 0, false, "", "", "", false, null, List.of(), SummaryType.NONE);
    }

    public FieldDefinition(long id, String name, FieldType type, int position, boolean required,
                           String defaultValue, String minValue, String maxValue, boolean uniqueValue,
                           Integer characterLimit, List<String> dropdownOptions, SummaryType summaryType) {
        this.id = id;
        this.name = Objects.requireNonNullElse(name, "Field").trim();
        this.type = Objects.requireNonNullElse(type, FieldType.TEXT);
        this.position = position;
        this.required = required;
        this.defaultValue = Objects.requireNonNullElse(defaultValue, "");
        this.minValue = Objects.requireNonNullElse(minValue, "");
        this.maxValue = Objects.requireNonNullElse(maxValue, "");
        this.uniqueValue = uniqueValue;
        this.characterLimit = characterLimit;
        this.dropdownOptions = new ArrayList<>(Objects.requireNonNullElse(dropdownOptions, List.of()));
        this.summaryType = Objects.requireNonNullElse(summaryType, SummaryType.NONE);
    }

    public FieldDefinition copy() {
        return new FieldDefinition(id, name, type, position, required, defaultValue, minValue, maxValue,
                uniqueValue, characterLimit, dropdownOptions, summaryType);
    }

    public long id() { return id; }
    public void setId(long id) { this.id = id; }
    public String name() { return name; }
    public void setName(String name) { this.name = Objects.requireNonNullElse(name, "").trim(); }
    public FieldType type() { return type; }
    public void setType(FieldType type) { this.type = Objects.requireNonNullElse(type, FieldType.TEXT); }
    public int position() { return position; }
    public void setPosition(int position) { this.position = position; }
    public boolean required() { return required; }
    public void setRequired(boolean required) { this.required = required; }
    public String defaultValue() { return defaultValue; }
    public void setDefaultValue(String defaultValue) { this.defaultValue = Objects.requireNonNullElse(defaultValue, ""); }
    public String minValue() { return minValue; }
    public void setMinValue(String minValue) { this.minValue = Objects.requireNonNullElse(minValue, ""); }
    public String maxValue() { return maxValue; }
    public void setMaxValue(String maxValue) { this.maxValue = Objects.requireNonNullElse(maxValue, ""); }
    public boolean uniqueValue() { return uniqueValue; }
    public void setUniqueValue(boolean uniqueValue) { this.uniqueValue = uniqueValue; }
    public Integer characterLimit() { return characterLimit; }
    public void setCharacterLimit(Integer characterLimit) { this.characterLimit = characterLimit; }
    public List<String> dropdownOptions() { return List.copyOf(dropdownOptions); }
    public void setDropdownOptions(List<String> dropdownOptions) { this.dropdownOptions = new ArrayList<>(Objects.requireNonNullElse(dropdownOptions, List.of())); }
    public SummaryType summaryType() { return summaryType; }
    public void setSummaryType(SummaryType summaryType) { this.summaryType = Objects.requireNonNullElse(summaryType, SummaryType.NONE); }

    @Override
    public String toString() {
        return name + " — " + type.displayName();
    }
}
