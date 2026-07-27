package org.pindb.util;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Small dependency-free JSON reader/writer for GitHub API data and PinDB metadata.
 */
public final class MiniJson {
    private MiniJson() {
    }

    public static Object parse(String json) {
        if (json == null) {
            throw new IllegalArgumentException("JSON text cannot be null.");
        }
        Parser parser = new Parser(json);
        Object value = parser.parseValue();
        parser.skipWhitespace();
        if (!parser.isEnd()) {
            throw parser.error("Unexpected trailing content");
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> array(Object value) {
        return value instanceof List<?> list ? (List<Object>) list : List.of();
    }

    public static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public static boolean bool(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(string(value));
    }

    public static String stringify(Object value) {
        StringBuilder builder = new StringBuilder();
        writeValue(builder, value);
        return builder.toString();
    }

    private static void writeValue(StringBuilder builder, Object value) {
        if (value == null) {
            builder.append("null");
        } else if (value instanceof String text) {
            writeString(builder, text);
        } else if (value instanceof Number || value instanceof Boolean) {
            builder.append(value);
        } else if (value instanceof Map<?, ?> map) {
            builder.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                writeString(builder, String.valueOf(entry.getKey()));
                builder.append(':');
                writeValue(builder, entry.getValue());
            }
            builder.append('}');
        } else if (value instanceof Iterable<?> iterable) {
            builder.append('[');
            boolean first = true;
            for (Object element : iterable) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                writeValue(builder, element);
            }
            builder.append(']');
        } else {
            writeString(builder, String.valueOf(value));
        }
    }

    private static void writeString(StringBuilder builder, String text) {
        builder.append('"');
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            switch (character) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (character < 0x20) {
                        builder.append(String.format("\\u%04x", (int) character));
                    } else {
                        builder.append(character);
                    }
                }
            }
        }
        builder.append('"');
    }

    private static final class Parser {
        private final String source;
        private int position;

        private Parser(String source) {
            this.source = source;
        }

        private Object parseValue() {
            skipWhitespace();
            if (isEnd()) {
                throw error("Expected a value");
            }
            return switch (source.charAt(position)) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> parseLiteral("true", Boolean.TRUE);
                case 'f' -> parseLiteral("false", Boolean.FALSE);
                case 'n' -> parseLiteral("null", null);
                default -> parseNumber();
            };
        }

        private Map<String, Object> parseObject() {
            expect('{');
            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            skipWhitespace();
            if (consume('}')) {
                return result;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                result.put(key, parseValue());
                skipWhitespace();
                if (consume('}')) {
                    return result;
                }
                expect(',');
            }
        }

        private List<Object> parseArray() {
            expect('[');
            ArrayList<Object> result = new ArrayList<>();
            skipWhitespace();
            if (consume(']')) {
                return result;
            }
            while (true) {
                result.add(parseValue());
                skipWhitespace();
                if (consume(']')) {
                    return result;
                }
                expect(',');
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder builder = new StringBuilder();
            while (!isEnd()) {
                char character = source.charAt(position++);
                if (character == '"') {
                    return builder.toString();
                }
                if (character != '\\') {
                    builder.append(character);
                    continue;
                }
                if (isEnd()) {
                    throw error("Unterminated escape sequence");
                }
                char escape = source.charAt(position++);
                switch (escape) {
                    case '"' -> builder.append('"');
                    case '\\' -> builder.append('\\');
                    case '/' -> builder.append('/');
                    case 'b' -> builder.append('\b');
                    case 'f' -> builder.append('\f');
                    case 'n' -> builder.append('\n');
                    case 'r' -> builder.append('\r');
                    case 't' -> builder.append('\t');
                    case 'u' -> builder.append(parseUnicode());
                    default -> throw error("Unsupported escape sequence: \\" + escape);
                }
            }
            throw error("Unterminated string");
        }

        private char parseUnicode() {
            if (position + 4 > source.length()) {
                throw error("Incomplete Unicode escape");
            }
            String digits = source.substring(position, position + 4);
            position += 4;
            try {
                return (char) Integer.parseInt(digits, 16);
            } catch (NumberFormatException exception) {
                throw error("Invalid Unicode escape: " + digits);
            }
        }

        private Object parseNumber() {
            int start = position;
            if (peek('-')) {
                position++;
            }
            readDigits();
            if (peek('.')) {
                position++;
                readDigits();
            }
            if (peek('e') || peek('E')) {
                position++;
                if (peek('+') || peek('-')) {
                    position++;
                }
                readDigits();
            }
            if (position == start) {
                throw error("Expected a JSON value");
            }
            String number = source.substring(start, position);
            try {
                BigDecimal decimal = new BigDecimal(number);
                if (!number.contains(".") && !number.contains("e") && !number.contains("E")) {
                    try {
                        return decimal.longValueExact();
                    } catch (ArithmeticException ignored) {
                        // Preserve very large integral values as BigDecimal.
                    }
                }
                return decimal;
            } catch (NumberFormatException exception) {
                throw error("Invalid number: " + number);
            }
        }

        private void readDigits() {
            int start = position;
            while (!isEnd() && Character.isDigit(source.charAt(position))) {
                position++;
            }
            if (start == position) {
                throw error("Expected a digit");
            }
        }

        private Object parseLiteral(String literal, Object value) {
            if (!source.startsWith(literal, position)) {
                throw error("Expected " + literal);
            }
            position += literal.length();
            return value;
        }

        private boolean consume(char expected) {
            if (peek(expected)) {
                position++;
                return true;
            }
            return false;
        }

        private void expect(char expected) {
            if (!consume(expected)) {
                throw error("Expected '" + expected + "'");
            }
        }

        private boolean peek(char expected) {
            return !isEnd() && source.charAt(position) == expected;
        }

        private void skipWhitespace() {
            while (!isEnd() && Character.isWhitespace(source.charAt(position))) {
                position++;
            }
        }

        private boolean isEnd() {
            return position >= source.length();
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at character " + position);
        }
    }
}
