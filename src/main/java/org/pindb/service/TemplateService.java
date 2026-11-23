package org.pindb.service;

import org.pindb.model.FieldDefinition;
import org.pindb.model.FieldType;
import org.pindb.model.SummaryType;

import java.util.ArrayList;
import java.util.List;

public final class TemplateService {
    public enum Template {
        BLANK("Blank Database", "Start with a single required Name field and build the database yourself."),
        COLLECTIONS("Collections", "Track books, movies, games, cards, collectibles, and other personal collections."),
        INVENTORY("Inventory", "Track quantities, locations, values, notes, and supporting documents for general inventory."),
        CONTACTS("Contacts", "Keep names, organizations, phone numbers, email addresses, addresses, and notes together."),
        HOUSEHOLD_BELONGINGS("Household Belongings", "Create a home inventory with purchase details, serial numbers, values, and receipts or photos."),
        VEHICLE_MAINTENANCE("Vehicle Maintenance", "Record vehicle service history, mileage, costs, repair shops, and upcoming maintenance."),
        PAYMENT_TRACKER("Payment Tracker", "Record dated payments, amounts, and notes."),
        EXPENSES("Expenses", "Track dated expenses by category with totals and descriptions."),
        TABLETOP_RPG("Tabletop RPG Campaign / Reference",
                "Organize system-neutral campaign material such as characters, NPCs, creatures, locations, factions, quests, items, abilities, and session notes.");

        private final String displayName;
        private final String description;

        Template(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String displayName() {
            return displayName;
        }

        public String description() {
            return description;
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
            case COLLECTIONS -> {
                FieldDefinition name = field("Name", FieldType.TEXT, 0);
                name.setRequired(true);
                fields.add(name);
                fields.add(dropdown("Category", 1,
                        List.of("Books", "Movies", "Games", "Cards", "Collectibles", "Other")));
                fields.add(field("Creator / Brand", FieldType.TEXT, 2));
                fields.add(field("Identifier / Serial Number", FieldType.TEXT, 3));

                FieldDefinition quantity = field("Quantity", FieldType.NUMBER, 4);
                quantity.setDefaultValue("1");
                quantity.setMinValue("0");
                quantity.setSummaryType(SummaryType.SUM);
                fields.add(quantity);

                FieldDefinition value = field("Estimated Value", FieldType.CURRENCY, 5);
                value.setMinValue("0");
                value.setSummaryType(SummaryType.SUM);
                fields.add(value);

                fields.add(dropdown("Condition", 6,
                        List.of("New", "Excellent", "Good", "Fair", "Poor", "Unknown")));
                fields.add(field("Location", FieldType.TEXT, 7));
                fields.add(field("Acquired", FieldType.DATE, 8));
                fields.add(field("Notes", FieldType.MULTILINE_TEXT, 9));
                fields.add(field("Photo / Document", FieldType.DOCUMENT, 10));
            }
            case INVENTORY -> {
                FieldDefinition item = field("Item", FieldType.TEXT, 0);
                item.setRequired(true);
                item.setUniqueValue(true);
                fields.add(item);

                FieldDefinition quantity = field("Quantity", FieldType.NUMBER, 1);
                quantity.setDefaultValue("1");
                quantity.setMinValue("0");
                quantity.setSummaryType(SummaryType.SUM);
                fields.add(quantity);

                fields.add(dropdown("Category", 2, List.of("General", "Food", "Equipment", "Supplies", "Other")));
                fields.add(field("Location", FieldType.TEXT, 3));

                FieldDefinition value = field("Value", FieldType.CURRENCY, 4);
                value.setMinValue("0");
                value.setSummaryType(SummaryType.SUM);
                fields.add(value);

                fields.add(field("Serial / Model", FieldType.TEXT, 5));
                FieldDefinition date = field("Last Updated", FieldType.DATE, 6);
                date.setDefaultValue("${TODAY}");
                fields.add(date);
                fields.add(field("Notes", FieldType.MULTILINE_TEXT, 7));
                fields.add(field("Document", FieldType.DOCUMENT, 8));
            }
            case CONTACTS -> {
                FieldDefinition name = field("Name", FieldType.TEXT, 0);
                name.setRequired(true);
                fields.add(name);
                fields.add(field("Organization", FieldType.TEXT, 1));
                fields.add(field("Email", FieldType.TEXT, 2));
                fields.add(field("Phone", FieldType.TEXT, 3));
                fields.add(field("Address", FieldType.MULTILINE_TEXT, 4));
                fields.add(field("Notes", FieldType.MULTILINE_TEXT, 5));
            }
            case HOUSEHOLD_BELONGINGS -> {
                FieldDefinition item = field("Item", FieldType.TEXT, 0);
                item.setRequired(true);
                fields.add(item);
                fields.add(dropdown("Category", 1,
                        List.of("Furniture", "Electronics", "Appliances", "Tools", "Jewelry", "Clothing", "Other")));
                fields.add(field("Room / Location", FieldType.TEXT, 2));
                fields.add(field("Brand / Model", FieldType.TEXT, 3));
                fields.add(field("Serial Number", FieldType.TEXT, 4));
                fields.add(field("Purchase Date", FieldType.DATE, 5));

                FieldDefinition purchasePrice = field("Purchase Price", FieldType.CURRENCY, 6);
                purchasePrice.setMinValue("0");
                purchasePrice.setSummaryType(SummaryType.SUM);
                fields.add(purchasePrice);

                FieldDefinition replacementValue = field("Estimated Replacement Value", FieldType.CURRENCY, 7);
                replacementValue.setMinValue("0");
                replacementValue.setSummaryType(SummaryType.SUM);
                fields.add(replacementValue);

                fields.add(field("Warranty Until", FieldType.DATE, 8));
                fields.add(field("Receipt / Photo / Document", FieldType.DOCUMENT, 9));
                fields.add(field("Notes", FieldType.MULTILINE_TEXT, 10));
            }
            case VEHICLE_MAINTENANCE -> {
                FieldDefinition vehicle = field("Vehicle", FieldType.TEXT, 0);
                vehicle.setRequired(true);
                fields.add(vehicle);

                FieldDefinition serviceDate = field("Service Date", FieldType.DATE, 1);
                serviceDate.setRequired(true);
                serviceDate.setDefaultValue("${TODAY}");
                fields.add(serviceDate);

                FieldDefinition mileage = field("Mileage", FieldType.NUMBER, 2);
                mileage.setMinValue("0");
                fields.add(mileage);

                FieldDefinition service = field("Service / Maintenance Performed", FieldType.MULTILINE_TEXT, 3);
                service.setRequired(true);
                fields.add(service);

                FieldDefinition cost = field("Cost", FieldType.CURRENCY, 4);
                cost.setMinValue("0");
                cost.setSummaryType(SummaryType.SUM);
                fields.add(cost);

                fields.add(field("Shop / Provider", FieldType.TEXT, 5));
                fields.add(field("Next Service Date", FieldType.DATE, 6));

                FieldDefinition nextMileage = field("Next Service Mileage", FieldType.NUMBER, 7);
                nextMileage.setMinValue("0");
                fields.add(nextMileage);

                fields.add(field("Receipt / Document", FieldType.DOCUMENT, 8));
                fields.add(field("Notes", FieldType.MULTILINE_TEXT, 9));
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
            case TABLETOP_RPG -> {
                FieldDefinition name = field("Name", FieldType.TEXT, 0);
                name.setRequired(true);
                fields.add(name);
                fields.add(dropdown("Type", 1, List.of(
                        "Character", "NPC", "Creature", "Location", "Faction", "Quest", "Encounter",
                        "Item", "Spell / Ability", "Session Note", "Campaign Note", "Other")));
                fields.add(field("Campaign", FieldType.TEXT, 2));
                fields.add(field("Status", FieldType.TEXT, 3));
                fields.add(field("Tags", FieldType.TEXT, 4));
                fields.add(field("Summary", FieldType.MULTILINE_TEXT, 5));
                fields.add(field("Details", FieldType.MULTILINE_TEXT, 6));
                fields.add(field("Related To", FieldType.TEXT, 7));
                fields.add(field("Session / Date", FieldType.TEXT, 8));
                fields.add(field("Document", FieldType.DOCUMENT, 9));
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
