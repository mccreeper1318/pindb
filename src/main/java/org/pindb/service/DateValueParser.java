package org.pindb.service;

import org.pindb.model.FieldType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class DateValueParser {
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            formatter("M/d/uuuu"),
            formatter("M/d/uu"),
            formatter("M-d-uuuu"),
            formatter("M-d-uu"),
            formatter("MMM d, uuuu"),
            formatter("MMMM d, uuuu")
    );

    private static final List<DateTimeFormatter> DATE_TIME_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            formatter("M/d/uuuu H:mm"),
            formatter("M/d/uuuu h:mm a"),
            formatter("M/d/uu H:mm"),
            formatter("M/d/uu h:mm a"),
            formatter("M-d-uuuu H:mm"),
            formatter("M-d-uuuu h:mm a"),
            formatter("MMM d, uuuu H:mm"),
            formatter("MMM d, uuuu h:mm a")
    );

    private DateValueParser() {
    }

    public static FieldType inferType(List<String> values) {
        TypeInference inference = new TypeInference();
        values.forEach(inference::accept);
        return inference.result();
    }

    public static TypeInference typeInference() {
        return new TypeInference();
    }

    public static final class TypeInference {
        private boolean sawValue;
        private boolean allDates = true;
        private boolean allDateTimes = true;

        private TypeInference() {
        }

        public void accept(String value) {
            String safe = value == null ? "" : value.trim();
            if (safe.isBlank()) {
                return;
            }
            sawValue = true;
            if (!looksLikeDateTime(safe) || parseDateTime(safe).isEmpty()) {
                allDateTimes = false;
            }
            if (!looksLikeDate(safe) || parseDate(safe).isEmpty()) {
                allDates = false;
            }
        }

        public FieldType result() {
            if (!sawValue) {
                return FieldType.TEXT;
            }
            if (allDateTimes) {
                return FieldType.DATE_TIME;
            }
            if (allDates) {
                return FieldType.DATE;
            }
            return FieldType.TEXT;
        }
    }

    public static String normalize(FieldType type, String value) {
        String safe = value == null ? "" : value.trim();
        if (safe.isBlank()) {
            return "";
        }
        return switch (type) {
            case DATE -> parseDate(safe).map(LocalDate::toString).orElse(safe);
            case DATE_TIME -> parseDateTime(safe).map(LocalDateTime::toString).orElse(safe);
            default -> safe;
        };
    }

    public static Optional<LocalDate> parseDate(String value) {
        String safe = value == null ? "" : value.trim();
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return Optional.of(LocalDate.parse(safe, formatter));
            } catch (DateTimeParseException ignored) {
                // Try the next supported format.
            }
        }
        return Optional.empty();
    }

    public static Optional<LocalDateTime> parseDateTime(String value) {
        String safe = value == null ? "" : value.trim();
        for (DateTimeFormatter formatter : DATE_TIME_FORMATS) {
            try {
                return Optional.of(LocalDateTime.parse(safe, formatter));
            } catch (DateTimeParseException ignored) {
                // Try the next supported format.
            }
        }
        return Optional.empty();
    }

    private static boolean looksLikeDate(String value) {
        return value.contains("/") || value.matches(".*\\d-\\d.*") || value.matches(".*[A-Za-z]{3,}.*");
    }

    private static boolean looksLikeDateTime(String value) {
        return looksLikeDate(value) && (value.contains(":") || value.contains("T"));
    }

    private static DateTimeFormatter formatter(String pattern) {
        return new DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .appendPattern(pattern)
                .toFormatter(Locale.US)
                .withResolverStyle(ResolverStyle.STRICT);
    }
}
