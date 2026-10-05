package org.pindb.db;

import org.pindb.model.FieldDefinition;
import org.pindb.model.FieldType;
import org.pindb.model.SummaryType;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class FieldDefinitionChangeValidator {
    private static final int MAX_REPORTED_VALUE_ERRORS = 8;

    private FieldDefinitionChangeValidator() {
    }

    static void validateDefinition(FieldDefinition field) {
        Objects.requireNonNull(field, "field");
        List<String> errors = new ArrayList<>();
        if (field.name().isBlank()) {
            errors.add("Enter a name for the field.");
        }
        if (field.characterLimit() != null && field.characterLimit() <= 0) {
            errors.add("Character limit must be greater than zero.");
        }

        if (field.type().isNumeric()) {
            BigDecimal minimum = parseBound(field.minValue(), "Minimum", errors);
            BigDecimal maximum = parseBound(field.maxValue(), "Maximum", errors);
            if (minimum != null && maximum != null && minimum.compareTo(maximum) > 0) {
                errors.add("Minimum cannot be greater than maximum.");
            }
        }

        if (field.type() == FieldType.DROPDOWN) {
            if (field.dropdownOptions().isEmpty()) {
                errors.add("Dropdown fields must contain at least one option.");
            } else if (field.dropdownOptions().stream().anyMatch(option -> option == null || option.isBlank())) {
                errors.add("Dropdown options cannot be blank.");
            }
        }

        if (field.type() == FieldType.DOCUMENT && !field.defaultValue().isBlank()) {
            errors.add("Document fields cannot have a default value.");
        } else if (!field.defaultValue().isBlank() && !isSupportedDynamicDefault(field)) {
            List<String> defaultErrors = valueErrors(field, field.defaultValue(), false);
            for (String error : defaultErrors) {
                errors.add("Default value " + lowerCaseFirst(error));
            }
        }

        if (!field.type().supportsSummary() && field.summaryType() != SummaryType.NONE) {
            errors.add("This field type does not support the selected summary.");
        }

        if (!errors.isEmpty()) {
            throw new DatabaseException("The field definition is invalid:\n" + String.join("\n", errors));
        }
    }

    static void validateAddition(Connection connection, FieldDefinition field) throws SQLException {
        validateDefinition(field);
        if (field.required() && recordCount(connection) > 0) {
            throw new DatabaseException("This field cannot be required while the database already contains entries. "
                    + "Add it as optional first, populate every active and Recently Deleted entry, then make it required.");
        }
    }

    static void validateUpdate(Connection connection, FieldDefinition current, FieldDefinition updated) throws SQLException {
        validateDefinition(updated);
        if (current.id() != updated.id()) {
            throw new DatabaseException("The field being edited no longer matches the stored field.");
        }

        validateDocumentTransition(connection, current, updated);

        List<String> errors = new ArrayList<>();
        int omitted = 0;
        String sql = "SELECT r.id,r.deleted_at,COALESCE(v.value,'') AS value "
                + "FROM records r LEFT JOIN record_values v ON v.record_id=r.id AND v.field_id=? ORDER BY r.id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, updated.id());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    List<String> valueErrors = valueErrors(updated, result.getString("value"), true);
                    if (valueErrors.isEmpty()) {
                        continue;
                    }
                    if (errors.size() < MAX_REPORTED_VALUE_ERRORS) {
                        String deleted = result.getString("deleted_at") == null ? "" : " (Recently Deleted)";
                        errors.add("Entry " + result.getLong("id") + deleted + ": " + String.join(" ", valueErrors));
                    } else {
                        omitted++;
                    }
                }
            }
        }

        if (updated.uniqueValue()) {
            String duplicate = firstDuplicateValue(connection, updated.id());
            if (duplicate != null) {
                errors.add("Values are not unique across active and Recently Deleted entries; “" + duplicate
                        + "” appears more than once.");
            }
        }

        if (updated.type() == FieldType.DOCUMENT) {
            validateDocumentLinks(connection, updated.id(), errors);
        }

        if (omitted > 0) {
            errors.add("…and " + omitted + " more incompatible entries.");
        }
        if (!errors.isEmpty()) {
            throw new DatabaseException("This field change would make existing data invalid:\n" + String.join("\n", errors));
        }
    }

    static void cleanupAfterUpdate(Connection connection, FieldDefinition current, FieldDefinition updated) throws SQLException {
        if (current.type() != FieldType.DOCUMENT || updated.type() == FieldType.DOCUMENT
                || !tableExists(connection, "document_values")) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM document_values WHERE field_id=?")) {
            statement.setLong(1, current.id());
            statement.executeUpdate();
        }
    }

    private static void validateDocumentTransition(Connection connection, FieldDefinition current,
                                                   FieldDefinition updated) throws SQLException {
        if (current.type() == updated.type()) {
            return;
        }
        boolean touchesDocument = current.type() == FieldType.DOCUMENT || updated.type() == FieldType.DOCUMENT;
        if (!touchesDocument) {
            return;
        }

        boolean documentsTable = tableExists(connection, "document_values");
        boolean backupsTable = tableExists(connection, "backup_document_values");
        if (documentsTable != backupsTable) {
            throw new DatabaseException("Embedded document storage is incomplete. Repair or restore the database before changing this field type.");
        }

        if (updated.type() == FieldType.DOCUMENT) {
            if (hasNonBlankValue(connection, current.id())) {
                throw new DatabaseException("Clear this field in every active and Recently Deleted entry before changing it to Document. "
                        + "Existing text cannot be converted into embedded files automatically.");
            }
            if (documentsTable && documentCount(connection, current.id()) > 0) {
                throw new DatabaseException("This field has embedded document data from an earlier state. "
                        + "Restore or remove that data before changing the field to Document.");
            }
            return;
        }

        if (documentsTable) {
            long documentCount = documentCount(connection, current.id());
            if (documentCount > 0) {
                throw new DatabaseException("This Document field still contains " + documentCount + " embedded file"
                        + (documentCount == 1 ? "" : "s") + ". Remove the documents from every active and Recently Deleted entry "
                        + "before changing the field type so no embedded data is discarded silently.");
            }
        }
    }

    private static void validateDocumentLinks(Connection connection, long fieldId, List<String> errors) throws SQLException {
        boolean documentsTable = tableExists(connection, "document_values");
        boolean backupsTable = tableExists(connection, "backup_document_values");
        if (documentsTable != backupsTable) {
            errors.add("Embedded document storage is incomplete.");
            return;
        }
        if (!documentsTable) {
            if (hasNonBlankValue(connection, fieldId)) {
                errors.add("Stored document filenames exist but embedded document storage is unavailable.");
            }
            return;
        }

        String sql = "SELECT r.id,r.deleted_at,COALESCE(v.value,'') AS value,d.file_name "
                + "FROM records r "
                + "LEFT JOIN record_values v ON v.record_id=r.id AND v.field_id=? "
                + "LEFT JOIN document_values d ON d.record_id=r.id AND d.field_id=? "
                + "WHERE (TRIM(COALESCE(v.value,''))<>'' AND d.record_id IS NULL) "
                + "OR (TRIM(COALESCE(v.value,''))='' AND d.record_id IS NOT NULL) "
                + "OR (d.record_id IS NOT NULL AND d.file_name<>v.value) "
                + "ORDER BY r.id LIMIT ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, fieldId);
            statement.setLong(2, fieldId);
            statement.setInt(3, MAX_REPORTED_VALUE_ERRORS);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String deleted = result.getString("deleted_at") == null ? "" : " (Recently Deleted)";
                    errors.add("Entry " + result.getLong("id") + deleted
                            + " has a document filename/BLOB mismatch. Open the entry and re-save or remove the document first.");
                }
            }
        }
    }

    private static List<String> valueErrors(FieldDefinition field, String rawValue, boolean enforceRequired) {
        String raw = Objects.requireNonNullElse(rawValue, "").trim();
        List<String> errors = new ArrayList<>();
        if (enforceRequired && field.required() && raw.isBlank()) {
            errors.add(field.name() + " is required.");
            return errors;
        }
        if (raw.isBlank()) {
            return errors;
        }
        if (field.characterLimit() != null && field.characterLimit() > 0 && raw.length() > field.characterLimit()) {
            errors.add(field.name() + " cannot be longer than " + field.characterLimit() + " characters.");
        }
        try {
            switch (field.type()) {
                case NUMBER, CURRENCY -> validateNumber(field, raw, errors);
                case DATE -> LocalDate.parse(raw);
                case DATE_TIME -> LocalDateTime.parse(raw);
                case BOOLEAN -> {
                    if (!raw.equalsIgnoreCase("true") && !raw.equalsIgnoreCase("false")) {
                        errors.add(field.name() + " must be Yes or No.");
                    }
                }
                case DROPDOWN -> {
                    if (field.dropdownOptions().stream().noneMatch(option -> option.equals(raw))) {
                        errors.add(field.name() + " contains an option that is not available.");
                    }
                }
                default -> {
                }
            }
        } catch (DateTimeParseException exception) {
            errors.add(field.name() + " contains an invalid date or time.");
        }
        return errors;
    }

    private static void validateNumber(FieldDefinition field, String raw, List<String> errors) {
        try {
            BigDecimal number = new BigDecimal(raw);
            if (!field.minValue().isBlank() && number.compareTo(new BigDecimal(field.minValue().trim())) < 0) {
                errors.add(field.name() + " must be at least " + field.minValue().trim() + ".");
            }
            if (!field.maxValue().isBlank() && number.compareTo(new BigDecimal(field.maxValue().trim())) > 0) {
                errors.add(field.name() + " cannot be greater than " + field.maxValue().trim() + ".");
            }
        } catch (NumberFormatException exception) {
            errors.add(field.name() + " must contain a valid number.");
        }
    }

    private static BigDecimal parseBound(String value, String label, List<String> errors) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException exception) {
            errors.add(label + " must be a valid number.");
            return null;
        }
    }

    private static boolean isSupportedDynamicDefault(FieldDefinition field) {
        return (field.type() == FieldType.DATE && "${TODAY}".equals(field.defaultValue()))
                || (field.type() == FieldType.DATE_TIME && "${NOW}".equals(field.defaultValue()));
    }

    private static String lowerCaseFirst(String value) {
        if (value == null || value.isEmpty()) {
            return "is invalid.";
        }
        return Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }

    private static long recordCount(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM records");
             ResultSet result = statement.executeQuery()) {
            return result.next() ? result.getLong(1) : 0;
        }
    }

    private static boolean hasNonBlankValue(Connection connection, long fieldId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM record_values WHERE field_id=? AND TRIM(value)<>'' LIMIT 1")) {
            statement.setLong(1, fieldId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private static String firstDuplicateValue(Connection connection, long fieldId) throws SQLException {
        String sql = "SELECT MIN(v.value) AS value FROM record_values v "
                + "WHERE v.field_id=? AND TRIM(v.value)<>'' "
                + "GROUP BY v.value COLLATE NOCASE HAVING COUNT(*)>1 LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, fieldId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getString("value") : null;
            }
        }
    }

    private static long documentCount(Connection connection, long fieldId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM document_values WHERE field_id=?")) {
            statement.setLong(1, fieldId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getLong(1) : 0;
            }
        }
    }

    private static boolean tableExists(Connection connection, String tableName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?")) {
            statement.setString(1, tableName);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }
}
