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
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
        Path source = csvFile.toAbsolutePath().normalize();
        Path target = destination.toAbsolutePath().normalize();
        if (Files.exists(target)) {
            throw new DatabaseException("A file already exists at " + target);
        }

        Path sourceSnapshot = snapshotCsvSource(source);
        Path temporary = temporaryImportPath(target);
        boolean published = false;
        try {
            ImportPlan plan = inspectCsv(sourceSnapshot);
            try (DatabaseService service = DatabaseService.create(temporary, databaseName,
                    "Imported from " + source.getFileName(), plan.fields(), DatabaseView.TABLE, 10)) {
                List<FieldDefinition> createdFields = service.fields();
                service.importRecords(consumer -> streamImportRows(sourceSnapshot, createdFields, consumer),
                        "CSV import completed");
                if (!service.integrityCheck()) {
                    throw new DatabaseException("The imported database failed its integrity check.");
                }
                service.checkpointWal();
            }

            publishAtomicallyNoClobber(temporary, target);
            cleanupTemporaryDatabase(temporary);
            published = true;
            return DatabaseService.open(target);
        } finally {
            deleteQuietly(sourceSnapshot);
            if (!published) {
                cleanupTemporaryDatabase(temporary);
            }
        }
    }

    public static List<List<String>> readCsv(Path source) {
        List<List<String>> rows = new ArrayList<>();
        forEachCsvRow(source, row -> rows.add(List.copyOf(row)));
        return rows;
    }

    private static ImportPlan inspectCsv(Path source) {
        CsvInspection inspection = new CsvInspection();
        forEachCsvRow(source, inspection::accept);
        return inspection.plan();
    }

    private static void streamImportRows(Path source, List<FieldDefinition> fields,
                                         DatabaseService.RecordImportConsumer consumer) {
        final boolean[] header = {true};
        forEachCsvRow(source, row -> {
            if (header[0]) {
                header[0] = false;
                return;
            }
            Map<Long, String> values = new LinkedHashMap<>();
            for (int column = 0; column < fields.size(); column++) {
                FieldDefinition field = fields.get(column);
                String raw = column < row.size() ? row.get(column) : "";
                values.put(field.id(), DateValueParser.normalize(field.type(), raw));
            }
            consumer.accept(values);
        });
    }

    private static void forEachCsvRow(Path source, CsvRowConsumer consumer) {
        try (BufferedReader reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
            parseRows(reader, consumer);
        } catch (DatabaseException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new DatabaseException("Could not read CSV file.", exception);
        }
    }

    private static void parseRows(BufferedReader reader, CsvRowConsumer consumer) throws IOException {
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
                consumer.accept(List.copyOf(row));
                row = new ArrayList<>();
                field.setLength(0);
            } else {
                field.append(current);
            }
        }
        if (!field.isEmpty() || !row.isEmpty()) {
            row.add(stripCarriageReturn(field.toString()));
            consumer.accept(List.copyOf(row));
        }
    }

    static Path snapshotCsvSource(Path source) {
        Path snapshot = null;
        try {
            BasicFileAttributes before = Files.readAttributes(source, BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            if (!before.isRegularFile()) {
                throw new DatabaseException("The selected CSV source is not a regular file.");
            }

            snapshot = Files.createTempFile("pindb-csv-source-", ".tmp");
            Files.copy(source, snapshot, StandardCopyOption.REPLACE_EXISTING);

            BasicFileAttributes after = Files.readAttributes(source, BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            if (!sameSourceVersion(before, after)) {
                throw new DatabaseException("The CSV file changed while PinDB was preparing the import. "
                        + "Try again after the file is no longer being edited.");
            }
            return snapshot;
        } catch (DatabaseException exception) {
            deleteQuietly(snapshot);
            throw exception;
        } catch (IOException exception) {
            deleteQuietly(snapshot);
            throw new DatabaseException("Could not create a stable snapshot of the CSV file.", exception);
        }
    }

    private static boolean sameSourceVersion(BasicFileAttributes before, BasicFileAttributes after) {
        if (before.size() != after.size() || !before.lastModifiedTime().equals(after.lastModifiedTime())) {
            return false;
        }
        Object beforeKey = before.fileKey();
        Object afterKey = after.fileKey();
        return beforeKey == null || afterKey == null || beforeKey.equals(afterKey);
    }

    private static Path temporaryImportPath(Path destination) {
        Path parent = destination.getParent();
        if (parent == null) {
            parent = Path.of(".").toAbsolutePath().normalize();
        }
        String fileName = destination.getFileName().toString();
        Path candidate;
        do {
            candidate = parent.resolve("." + fileName + ".import-" + UUID.randomUUID() + ".tmp");
        } while (Files.exists(candidate));
        return candidate;
    }

    static void publishAtomicallyNoClobber(Path temporary, Path destination) {
        try {
            Files.createLink(destination, temporary);
        } catch (FileAlreadyExistsException exception) {
            throw new DatabaseException("A file was created at " + destination
                    + " while the CSV import was running. The imported database was not published.", exception);
        } catch (UnsupportedOperationException exception) {
            throw new DatabaseException("This filesystem does not support safe atomic CSV import publication.", exception);
        } catch (IOException exception) {
            throw new DatabaseException("Could not atomically publish the imported database without overwriting "
                    + "an existing file.", exception);
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Preserve the original import result or failure.
        }
    }

    private static void cleanupTemporaryDatabase(Path temporary) {
        for (String suffix : List.of("", "-wal", "-shm")) {
            try {
                Files.deleteIfExists(Path.of(temporary.toString() + suffix));
            } catch (IOException ignored) {
                // Preserve the original import failure; temporary files are never published as the destination.
            }
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

    @FunctionalInterface
    private interface CsvRowConsumer {
        void accept(List<String> row);
    }

    private record ImportPlan(List<FieldDefinition> fields) {
        private ImportPlan {
            fields = List.copyOf(fields);
        }
    }

    private static final class CsvInspection {
        private List<String> headers;
        private List<DateValueParser.TypeInference> inferences;

        private void accept(List<String> row) {
            if (headers == null) {
                headers = normalizeHeaders(row);
                inferences = new ArrayList<>(headers.size());
                for (int index = 0; index < headers.size(); index++) {
                    inferences.add(DateValueParser.typeInference());
                }
                return;
            }
            for (int column = 0; column < inferences.size(); column++) {
                inferences.get(column).accept(column < row.size() ? row.get(column) : "");
            }
        }

        private ImportPlan plan() {
            if (headers == null || headers.isEmpty()) {
                throw new DatabaseException("The CSV file does not contain a header row.");
            }
            List<FieldDefinition> fields = new ArrayList<>(headers.size());
            for (int index = 0; index < headers.size(); index++) {
                FieldType inferredType = inferences.get(index).result();
                fields.add(new FieldDefinition(0, headers.get(index), inferredType, index, false,
                        "", "", "", false, null, List.of(), SummaryType.NONE));
            }
            return new ImportPlan(fields);
        }

        private static List<String> normalizeHeaders(List<String> row) {
            if (row.isEmpty()) {
                throw new DatabaseException("The CSV file does not contain a header row.");
            }
            List<String> normalized = new ArrayList<>(row.size());
            Set<String> seen = new HashSet<>();
            for (int index = 0; index < row.size(); index++) {
                String header = row.get(index).isBlank() ? "Field " + (index + 1) : row.get(index).trim();
                String key = header.toLowerCase(Locale.ROOT);
                if (!seen.add(key)) {
                    throw new DatabaseException("The CSV header contains duplicate field name “" + header
                            + "”. Rename duplicate columns before importing.");
                }
                normalized.add(header);
            }
            return List.copyOf(normalized);
        }
    }
}
