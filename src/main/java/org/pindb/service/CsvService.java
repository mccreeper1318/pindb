package org.pindb.service;

import org.pindb.db.DatabaseException;
import org.pindb.db.DatabaseService;
import org.pindb.model.DatabaseView;
import org.pindb.model.FieldDefinition;
import org.pindb.model.FieldType;
import org.pindb.model.RecordData;
import org.pindb.model.SummaryType;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CsvService {
    private CsvService() {
    }

    public static void exportCsv(Path destination, List<FieldDefinition> fields, List<RecordData> records) {
        try (BufferedWriter writer = Files.newBufferedWriter(destination, StandardCharsets.UTF_8)) {
            writer.write(fields.stream().map(FieldDefinition::name).map(CsvService::quote).reduce((a, b) -> a + "," + b).orElse(""));
            writer.newLine();
            for (RecordData record : records) {
                for (int index = 0; index < fields.size(); index++) {
                    if (index > 0) {
                        writer.write(',');
                    }
                    writer.write(quote(record.value(fields.get(index).id())));
                }
                writer.newLine();
            }
        } catch (IOException exception) {
            throw new DatabaseException("Could not export CSV file.", exception);
        }
    }

    public static DatabaseService importCsv(Path csvFile, Path destination, String databaseName) {
        List<List<String>> rows = readCsv(csvFile);
        if (rows.isEmpty() || rows.getFirst().isEmpty()) {
            throw new DatabaseException("The CSV file does not contain a header row.");
        }
        List<String> headers = rows.getFirst();
        List<FieldDefinition> fields = new ArrayList<>();
        for (int index = 0; index < headers.size(); index++) {
            String header = headers.get(index).isBlank() ? "Field " + (index + 1) : headers.get(index).trim();
            int column = index;
            List<String> columnValues = rows.stream().skip(1)
                    .map(row -> column < row.size() ? row.get(column) : "")
                    .toList();
            FieldType inferredType = DateValueParser.inferType(columnValues);
            fields.add(new FieldDefinition(0, header, inferredType, index, false,
                    "", "", "", false, null, List.of(), SummaryType.NONE));
        }
        DatabaseService service = DatabaseService.create(destination, databaseName,
                "Imported from " + csvFile.getFileName(), fields, DatabaseView.TABLE, 10);
        List<FieldDefinition> createdFields = service.fields();
        for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
            List<String> row = rows.get(rowIndex);
            Map<Long, String> values = new LinkedHashMap<>();
            for (int column = 0; column < createdFields.size(); column++) {
                FieldDefinition field = createdFields.get(column);
                String raw = column < row.size() ? row.get(column) : "";
                values.put(field.id(), DateValueParser.normalize(field.type(), raw));
            }
            service.addRecord(values);
        }
        return service;
    }

    public static List<List<String>> readCsv(Path source) {
        try (BufferedReader reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
            List<List<String>> rows = new ArrayList<>();
            List<String> row = new ArrayList<>();
            StringBuilder field = new StringBuilder();
            boolean quoted = false;
            int character;
            while ((character = reader.read()) != -1) {
                char current = (char) character;
                if (quoted) {
                    if (current == '"') {
                        reader.mark(1);
                        int next = reader.read();
                        if (next == '"') {
                            field.append('"');
                        } else {
                            quoted = false;
                            if (next != -1) {
                                reader.reset();
                            }
                        }
                    } else {
                        field.append(current);
                    }
                } else if (current == '"' && field.isEmpty()) {
                    quoted = true;
                } else if (current == ',') {
                    row.add(field.toString());
                    field.setLength(0);
                } else if (current == '\n') {
                    row.add(stripCarriageReturn(field.toString()));
                    rows.add(row);
                    row = new ArrayList<>();
                    field.setLength(0);
                } else {
                    field.append(current);
                }
            }
            if (!field.isEmpty() || !row.isEmpty()) {
                row.add(stripCarriageReturn(field.toString()));
                rows.add(row);
            }
            return rows;
        } catch (IOException exception) {
            throw new DatabaseException("Could not read CSV file.", exception);
        }
    }

    private static String quote(String value) {
        String safe = value == null ? "" : value;
        if (safe.contains(",") || safe.contains("\n") || safe.contains("\r") || safe.contains("\"")) {
            return '"' + safe.replace("\"", "\"\"") + '"';
        }
        return safe;
    }

    private static String stripCarriageReturn(String value) {
        return value.endsWith("\r") ? value.substring(0, value.length() - 1) : value;
    }
}
