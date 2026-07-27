package org.pindb.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

public record FilterSpec(
        Long dateFieldId,
        LocalDate dateFrom,
        LocalDate dateTo,
        Long numericFieldId,
        BigDecimal numericMinimum,
        BigDecimal numericMaximum,
        Long choiceFieldId,
        String choiceValue
) {
    public static FilterSpec empty() {
        return new FilterSpec(null, null, null, null, null, null, null, "");
    }

    public boolean isEmpty() {
        return dateFieldId == null && numericFieldId == null && choiceFieldId == null;
    }

    public boolean matches(RecordData record, Map<Long, FieldDefinition> fields) {
        if (dateFieldId != null) {
            String value = record.value(dateFieldId);
            if (value.isBlank()) {
                return false;
            }
            try {
                FieldDefinition field = fields.get(dateFieldId);
                LocalDate date = field != null && field.type() == FieldType.DATE_TIME
                        ? LocalDateTime.parse(value).toLocalDate()
                        : LocalDate.parse(value);
                if (dateFrom != null && date.isBefore(dateFrom)) {
                    return false;
                }
                if (dateTo != null && date.isAfter(dateTo)) {
                    return false;
                }
            } catch (RuntimeException exception) {
                return false;
            }
        }

        if (numericFieldId != null) {
            try {
                BigDecimal value = new BigDecimal(record.value(numericFieldId));
                if (numericMinimum != null && value.compareTo(numericMinimum) < 0) {
                    return false;
                }
                if (numericMaximum != null && value.compareTo(numericMaximum) > 0) {
                    return false;
                }
            } catch (RuntimeException exception) {
                return false;
            }
        }

        if (choiceFieldId != null && choiceValue != null && !choiceValue.isBlank()) {
            if (!record.value(choiceFieldId).equalsIgnoreCase(choiceValue)) {
                return false;
            }
        }
        return true;
    }
}
