package org.pindb.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record SavedView(
        String id,
        String name,
        String searchText,
        FilterSpec filterSpec,
        List<SortSpec> sortOrder
) {
    public SavedView {
        id = Objects.requireNonNullElse(id, "");
        name = Objects.requireNonNullElse(name, "").trim();
        searchText = Objects.requireNonNullElse(searchText, "");
        filterSpec = Objects.requireNonNullElse(filterSpec, FilterSpec.empty());
        sortOrder = List.copyOf(Objects.requireNonNullElse(sortOrder, List.of()));
    }

    public List<String> validationProblems(Map<Long, FieldDefinition> fields) {
        List<String> problems = new ArrayList<>();
        validateField(fields, filterSpec.dateFieldId(), "date filter",
                field -> field.type() == FieldType.DATE || field.type() == FieldType.DATE_TIME, problems);
        validateField(fields, filterSpec.numericFieldId(), "number filter",
                field -> field.type().isNumeric(), problems);
        validateField(fields, filterSpec.choiceFieldId(), "choice filter",
                field -> field.type() == FieldType.DROPDOWN || field.type() == FieldType.BOOLEAN, problems);

        if (filterSpec.choiceFieldId() != null && filterSpec.choiceValue() != null
                && !filterSpec.choiceValue().isBlank()) {
            FieldDefinition field = fields.get(filterSpec.choiceFieldId());
            if (field != null && field.type() == FieldType.DROPDOWN && !field.dropdownOptions().isEmpty()
                    && field.dropdownOptions().stream().noneMatch(option -> option.equals(filterSpec.choiceValue()))) {
                problems.add("The choice filter value “" + filterSpec.choiceValue()
                        + "” is no longer available in “" + field.name() + "”.");
            }
        }

        for (SortSpec sort : sortOrder) {
            if (!fields.containsKey(sort.fieldId())) {
                problems.add("A sorted field used by this view no longer exists.");
            }
        }
        return List.copyOf(problems);
    }

    public boolean isMeaningful() {
        return !searchText.isBlank() || !filterSpec.isEmpty() || !sortOrder.isEmpty();
    }

    private static void validateField(Map<Long, FieldDefinition> fields,
                                      Long fieldId,
                                      String purpose,
                                      java.util.function.Predicate<FieldDefinition> supported,
                                      List<String> problems) {
        if (fieldId == null) {
            return;
        }
        FieldDefinition field = fields.get(fieldId);
        if (field == null) {
            problems.add("The field used by the " + purpose + " no longer exists.");
        } else if (!supported.test(field)) {
            problems.add("“" + field.name() + "” no longer supports the saved " + purpose + ".");
        }
    }

    public record SortSpec(long fieldId, boolean ascending) {
    }
}
