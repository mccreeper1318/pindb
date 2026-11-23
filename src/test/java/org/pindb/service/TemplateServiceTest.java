package org.pindb.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pindb.db.DatabaseService;
import org.pindb.model.DatabaseView;
import org.pindb.model.FieldDefinition;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TemplateServiceTest {
    @TempDir
    Path tempDirectory;

    @Test
    void everyTemplateHasMetadataAndCreatesANormalDatabase() {
        for (TemplateService.Template template : TemplateService.Template.values()) {
            assertFalse(template.displayName().isBlank(), template.name() + " should have a display name");
            assertFalse(template.description().isBlank(), template.name() + " should have a description");

            List<FieldDefinition> templateFields = TemplateService.fieldsFor(template);
            assertFalse(templateFields.isEmpty(), template.name() + " should create at least one field");

            Set<String> names = new HashSet<>();
            for (int i = 0; i < templateFields.size(); i++) {
                FieldDefinition field = templateFields.get(i);
                assertEquals(i, field.position(), template.name() + " field positions should be sequential");
                assertTrue(names.add(field.name().toLowerCase(Locale.ROOT)),
                        template.name() + " should not contain duplicate field names");
            }

            Path file = tempDirectory.resolve(template.name().toLowerCase(Locale.ROOT) + ".pindb");
            try (DatabaseService database = DatabaseService.create(file, template.displayName(), template.description(),
                    templateFields, DatabaseView.TABLE, 3)) {
                assertTrue(database.integrityCheck());
                assertEquals(templateFields.size(), database.fields().size());
                for (int i = 0; i < templateFields.size(); i++) {
                    FieldDefinition expected = templateFields.get(i);
                    FieldDefinition actual = database.fields().get(i);
                    assertEquals(expected.name(), actual.name());
                    assertEquals(expected.type(), actual.type());
                    assertEquals(expected.position(), actual.position());
                    assertEquals(expected.required(), actual.required());
                    assertEquals(expected.defaultValue(), actual.defaultValue());
                    assertEquals(expected.minValue(), actual.minValue());
                    assertEquals(expected.maxValue(), actual.maxValue());
                    assertEquals(expected.uniqueValue(), actual.uniqueValue());
                    assertEquals(expected.dropdownOptions(), actual.dropdownOptions());
                    assertEquals(expected.summaryType(), actual.summaryType());
                }
            }
        }
    }

    @Test
    void issue138TemplatesExposeTheExpectedCoreFields() {
        assertFieldNames(TemplateService.Template.COLLECTIONS,
                "Name", "Category", "Creator / Brand", "Identifier / Serial Number", "Quantity",
                "Estimated Value", "Condition", "Location", "Acquired", "Notes", "Photo / Document");
        assertFieldNames(TemplateService.Template.INVENTORY,
                "Item", "Quantity", "Category", "Location", "Value", "Serial / Model",
                "Last Updated", "Notes", "Document");
        assertFieldNames(TemplateService.Template.CONTACTS,
                "Name", "Organization", "Email", "Phone", "Address", "Notes");
        assertFieldNames(TemplateService.Template.HOUSEHOLD_BELONGINGS,
                "Item", "Category", "Room / Location", "Brand / Model", "Serial Number", "Purchase Date",
                "Purchase Price", "Estimated Replacement Value", "Warranty Until",
                "Receipt / Photo / Document", "Notes");
        assertFieldNames(TemplateService.Template.VEHICLE_MAINTENANCE,
                "Vehicle", "Service Date", "Mileage", "Service / Maintenance Performed", "Cost",
                "Shop / Provider", "Next Service Date", "Next Service Mileage", "Receipt / Document", "Notes");
        assertFieldNames(TemplateService.Template.TABLETOP_RPG,
                "Name", "Type", "Campaign", "Status", "Tags", "Summary", "Details",
                "Related To", "Session / Date", "Document");
    }

    @Test
    void returnedTemplateFieldsAreIndependentCopies() {
        List<FieldDefinition> first = TemplateService.fieldsFor(TemplateService.Template.COLLECTIONS);
        first.getFirst().setName("Changed");
        first.get(1).setDropdownOptions(List.of("Changed"));

        List<FieldDefinition> second = TemplateService.fieldsFor(TemplateService.Template.COLLECTIONS);
        assertEquals("Name", second.getFirst().name());
        assertNotEquals(List.of("Changed"), second.get(1).dropdownOptions());
    }

    private static void assertFieldNames(TemplateService.Template template, String... expectedNames) {
        assertEquals(List.of(expectedNames),
                TemplateService.fieldsFor(template).stream().map(FieldDefinition::name).toList());
    }
}
