package org.pindb.service;

import org.pindb.model.FieldDefinition;
import org.pindb.model.FieldType;
import org.pindb.model.SummaryType;

import java.util.ArrayList;
import java.util.List;

public final class TemplateService {
    public enum Template {
        BLANK("Blank Database"),
        PAYMENT_TRACKER("Payment Tracker"),
        INVENTORY("Inventory"),
        CONTACTS("Contacts"),
        EXPENSES("Expenses");

        private final String displayName;

        Template(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    private TemplateService() {
    }

    public static List<FieldDefinition> fieldsFor(Template template) {
        List<FieldDefinition> fields = new ArrayList<>();
        switch (template) {
            case BLANK -> {
                FieldDefinition field = field("Name", FieldType.TEXT, 0);
                field.setRequired(true);
                fields.add(field);
            }
            case PAYMENT_TRACKER -> {
                FieldDefinition date = field("Date", FieldType.DATE, 0);
                date.setRequired(true);
                date.setDefaultValue("${TODAY}");
                fields.add(date);

                FieldDefinition amount = field("Amount Paid", FieldType.CURRENCY, 1);
                amount.setRequired(true);
                amount.setSummaryType(SummaryType.SUM);
                fields.add(amount);

                fields.add(field("Notes", FieldType.MULTILINE_TEXT, 2));
            }
            case INVENTORY -> {
                FieldDefinition item = field("Item", FieldType.TEXT, 0);
                item.setRequired(true);
                item.setUniqueValue(true);
                fields.add(item);

                FieldDefinition quantity = field("Quantity", FieldType.NUMBER, 1);
                quantity.setDefaultValue("0");
                quantity.setSummaryType(SummaryType.SUM);
                fields.add(quantity);

                FieldDefinition cost = field("Unit Cost", FieldType.CURRENCY, 2);
                cost.setSummaryType(SummaryType.AVERAGE);
                fields.add(cost);

                fields.add(dropdown("Category", 3, List.of("General", "Food", "Equipment", "Supplies")));
                FieldDefinition date = field("Last Updated", FieldType.DATE, 4);
                date.setDefaultValue("${TODAY}");
                fields.add(date);
            }
            case CONTACTS -> {
                FieldDefinition name = field("Name", FieldType.TEXT, 0);
                name.setRequired(true);
                fields.add(name);
                fields.add(field("Email", FieldType.TEXT, 1));
                fields.add(field("Phone", FieldType.TEXT, 2));
                fields.add(field("Notes", FieldType.MULTILINE_TEXT, 3));
            }
            case EXPENSES -> {
                FieldDefinition date = field("Date", FieldType.DATE, 0);
                date.setDefaultValue("${TODAY}");
                date.setRequired(true);
                fields.add(date);
                fields.add(dropdown("Category", 1,
                        List.of("Housing", "Food", "Transportation", "Utilities", "Medical", "Other")));
                FieldDefinition amount = field("Amount", FieldType.CURRENCY, 2);
                amount.setRequired(true);
                amount.setSummaryType(SummaryType.SUM);
                fields.add(amount);
                fields.add(field("Description", FieldType.MULTILINE_TEXT, 3));
            }
        }
        return fields;
    }

    private static FieldDefinition field(String name, FieldType type, int position) {
        return new FieldDefinition(0, name, type, position, false, "", "", "", false,
                null, List.of(), SummaryType.NONE);
    }

    private static FieldDefinition dropdown(String name, int position, List<String> options) {
        FieldDefinition definition = field(name, FieldType.DROPDOWN, position);
        definition.setDropdownOptions(options);
        return definition;
    }
}
