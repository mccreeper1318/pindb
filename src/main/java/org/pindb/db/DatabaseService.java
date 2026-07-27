package org.pindb.db;

import org.pindb.model.BackupSnapshot;
import org.pindb.model.DatabaseInfo;
import org.pindb.model.DatabaseView;
import org.pindb.model.FieldDefinition;
import org.pindb.model.FieldType;
import org.pindb.model.RecordData;
import org.pindb.model.SummaryType;
import org.pindb.util.MiniJson;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class DatabaseService implements AutoCloseable {
    private final Path path;
    private Connection connection;

    private DatabaseService(Path path) {
        this.path = path.toAbsolutePath().normalize();
    }

    public static DatabaseService create(Path path, String name, String description,
                                         List<FieldDefinition> fields, DatabaseView defaultView,
                                         int backupLimit) {
        Objects.requireNonNull(path, "path");
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (Files.exists(path)) {
                throw new DatabaseException("A file already exists at " + path);
            }
            DatabaseService service = new DatabaseService(path);
            service.openConnection();
            service.initializeSchema();
            service.transaction(() -> {
                service.setMetaInternal("database_name", name == null || name.isBlank() ? "Untitled Database" : name.trim());
                service.setMetaInternal("description", Objects.requireNonNullElse(description, ""));
                service.setMetaInternal("schema_version", String.valueOf(DatabaseMigrator.CURRENT_SCHEMA_VERSION));
                service.setMetaInternal("created_at", LocalDateTime.now().toString());
                service.setMetaInternal("default_view", Objects.requireNonNullElse(defaultView, DatabaseView.TABLE).name());
                service.setMetaInternal("suppress_delete_confirmation", "false");
                service.setMetaInternal("backup_limit", String.valueOf(Math.max(1, backupLimit)));
                service.setMetaInternal("print_layout", "COLUMNS");
                service.setMetaInternal("print_orientation", "LANDSCAPE");
                int position = 0;
                for (FieldDefinition definition : fields) {
                    FieldDefinition copy = definition.copy();
                    copy.setPosition(position++);
                    service.insertFieldInternal(copy);
                }
                return null;
            });
            service.createSnapshot("Database created");
            return service;
        } catch (DatabaseException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new DatabaseException("Could not create PinDB database.", exception);
        }
    }

    public static DatabaseService open(Path path) {
        Objects.requireNonNull(path, "path");
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException exception) {
            throw new DatabaseException("The SQLite driver is not available.", exception);
        }
        DatabaseMigrator.ensureCompatible(path.toAbsolutePath().normalize());
        DatabaseService service = new DatabaseService(path);
        service.openConnection();
        return service;
    }

    private void openConnection() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + path);
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys=ON");
                statement.execute("PRAGMA busy_timeout=5000");
                statement.execute("PRAGMA journal_mode=WAL");
                statement.execute("PRAGMA synchronous=NORMAL");
            }
        } catch (SQLException | ClassNotFoundException exception) {
            throw new DatabaseException("Could not open database: " + path, exception);
        }
    }

    private void initializeSchema() {
        String[] statements = {
                "CREATE TABLE pindb_meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)",
                "CREATE TABLE field_definitions ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "name TEXT NOT NULL, field_type TEXT NOT NULL, position INTEGER NOT NULL, "
                        + "required INTEGER NOT NULL DEFAULT 0, default_value TEXT NOT NULL DEFAULT '', "
                        + "min_value TEXT NOT NULL DEFAULT '', max_value TEXT NOT NULL DEFAULT '', "
                        + "unique_value INTEGER NOT NULL DEFAULT 0, char_limit INTEGER, "
                        + "dropdown_options TEXT NOT NULL DEFAULT '[]', summary_type TEXT NOT NULL DEFAULT 'NONE')",
                "CREATE TABLE records ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, created_at TEXT NOT NULL, "
                        + "updated_at TEXT NOT NULL, deleted_at TEXT)",
                "CREATE TABLE record_values ("
                        + "record_id INTEGER NOT NULL, field_id INTEGER NOT NULL, value TEXT NOT NULL DEFAULT '', "
                        + "PRIMARY KEY(record_id, field_id), "
                        + "FOREIGN KEY(record_id) REFERENCES records(id) ON DELETE CASCADE, "
                        + "FOREIGN KEY(field_id) REFERENCES field_definitions(id) ON DELETE CASCADE)",
                "CREATE INDEX idx_records_deleted_at ON records(deleted_at)",
                "CREATE INDEX idx_values_field_value ON record_values(field_id, value)",
                "CREATE TABLE backup_snapshots ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, created_at TEXT NOT NULL, reason TEXT NOT NULL)",
                "CREATE TABLE backup_meta (snapshot_id INTEGER NOT NULL, key TEXT NOT NULL, value TEXT NOT NULL, "
                        + "PRIMARY KEY(snapshot_id,key))",
                "CREATE TABLE backup_fields (snapshot_id INTEGER NOT NULL, field_id INTEGER NOT NULL, "
                        + "name TEXT NOT NULL, field_type TEXT NOT NULL, position INTEGER NOT NULL, "
                        + "required INTEGER NOT NULL, default_value TEXT NOT NULL, min_value TEXT NOT NULL, "
                        + "max_value TEXT NOT NULL, unique_value INTEGER NOT NULL, char_limit INTEGER, "
                        + "dropdown_options TEXT NOT NULL, summary_type TEXT NOT NULL, "
                        + "PRIMARY KEY(snapshot_id,field_id))",
                "CREATE TABLE backup_records (snapshot_id INTEGER NOT NULL, record_id INTEGER NOT NULL, "
                        + "created_at TEXT NOT NULL, updated_at TEXT NOT NULL, deleted_at TEXT, "
                        + "PRIMARY KEY(snapshot_id,record_id))",
                "CREATE TABLE backup_values (snapshot_id INTEGER NOT NULL, record_id INTEGER NOT NULL, "
                        + "field_id INTEGER NOT NULL, value TEXT NOT NULL, "
                        + "PRIMARY KEY(snapshot_id,record_id,field_id))"
        };
        try (Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Could not initialize the database schema.", exception);
        }
    }

    public Path path() {
        return path;
    }

    public DatabaseInfo info() {
        return new DatabaseInfo(
                path,
                getMeta("database_name", path.getFileName().toString()),
                getMeta("description", ""),
                Integer.parseInt(getMeta("schema_version", "1")),
                parseEnum(DatabaseView.class, getMeta("default_view", "TABLE"), DatabaseView.TABLE),
                Boolean.parseBoolean(getMeta("suppress_delete_confirmation", "false")),
                Math.max(1, parseInteger(getMeta("backup_limit", "10"), 10))
        );
    }

    public String getMeta(String key, String fallback) {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT value FROM pindb_meta WHERE key=?")) {
            statement.setString(1, key);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getString(1) : fallback;
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Could not read database setting: " + key, exception);
        }
    }

    public void setMeta(String key, String value) {
        transaction(() -> {
            setMetaInternal(key, value);
            return null;
        });
    }

    private void setMetaInternal(String key, String value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO pindb_meta(key,value) VALUES(?,?) "
                        + "ON CONFLICT(key) DO UPDATE SET value=excluded.value")) {
            statement.setString(1, key);
            statement.setString(2, Objects.requireNonNullElse(value, ""));
            statement.executeUpdate();
        }
    }

    public List<FieldDefinition> fields() {
        List<FieldDefinition> fields = new ArrayList<>();
        String sql = "SELECT id,name,field_type,position,required,default_value,min_value,max_value,"
                + "unique_value,char_limit,dropdown_options,summary_type "
                + "FROM field_definitions ORDER BY position,id";
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            while (result.next()) {
                fields.add(readField(result));
            }
            return fields;
        } catch (SQLException exception) {
            throw new DatabaseException("Could not load database fields.", exception);
        }
    }

    public List<RecordData> activeRecords() {
        return records(false);
    }

    public List<RecordData> deletedRecords() {
        return records(true);
    }

    private List<RecordData> records(boolean deleted) {
        String condition = deleted ? "IS NOT NULL" : "IS NULL";
        LinkedHashMap<Long, RecordDataBuilder> builders = new LinkedHashMap<>();
        String sql = "SELECT r.id,r.created_at,r.updated_at,r.deleted_at,v.field_id,v.value "
                + "FROM records r LEFT JOIN record_values v ON v.record_id=r.id "
                + "WHERE r.deleted_at " + condition + " ORDER BY r.id DESC,v.field_id";
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            while (result.next()) {
                long recordId = result.getLong("id");
                RecordDataBuilder builder = builders.computeIfAbsent(recordId, ignored -> new RecordDataBuilder(
                        recordId,
                        LocalDateTime.parse(resultString(result, "created_at")),
                        LocalDateTime.parse(resultString(result, "updated_at")),
                        nullableDateTime(resultString(result, "deleted_at"))
                ));
                long fieldId = result.getLong("field_id");
                if (!result.wasNull()) {
                    builder.values.put(fieldId, result.getString("value"));
                }
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Could not load database entries.", exception);
        }
        return builders.values().stream().map(RecordDataBuilder::build).toList();
    }

    public long countActiveRecords() {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM records WHERE deleted_at IS NULL")) {
            return result.next() ? result.getLong(1) : 0;
        } catch (SQLException exception) {
            throw new DatabaseException("Could not count database entries.", exception);
        }
    }

    public long addRecord(Map<Long, String> values) {
        ensureValidValues(values, null);
        createSnapshot("Before adding entry");
        return transaction(() -> {
            LocalDateTime now = LocalDateTime.now();
            long recordId;
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO records(created_at,updated_at,deleted_at) VALUES(?,?,NULL)",
                    Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, now.toString());
                statement.setString(2, now.toString());
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("SQLite did not return a record ID.");
                    }
                    recordId = keys.getLong(1);
                }
            }
            writeValues(recordId, values);
            return recordId;
        });
    }

    public void updateRecord(long recordId, Map<Long, String> values) {
        ensureValidValues(values, recordId);
        createSnapshot("Before editing entry " + recordId);
        transaction(() -> {
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE records SET updated_at=? WHERE id=? AND deleted_at IS NULL")) {
                update.setString(1, LocalDateTime.now().toString());
                update.setLong(2, recordId);
                if (update.executeUpdate() == 0) {
                    throw new SQLException("The selected entry no longer exists.");
                }
            }
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM record_values WHERE record_id=?")) {
                delete.setLong(1, recordId);
                delete.executeUpdate();
            }
            writeValues(recordId, values);
            return null;
        });
    }

    public void moveToTrash(long recordId) {
        createSnapshot("Before moving entry " + recordId + " to trash");
        transaction(() -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE records SET deleted_at=?,updated_at=? WHERE id=? AND deleted_at IS NULL")) {
                String now = LocalDateTime.now().toString();
                statement.setString(1, now);
                statement.setString(2, now);
                statement.setLong(3, recordId);
                statement.executeUpdate();
            }
            return null;
        });
    }

    public void restoreRecord(long recordId) {
        createSnapshot("Before restoring entry " + recordId);
        transaction(() -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE records SET deleted_at=NULL,updated_at=? WHERE id=?")) {
                statement.setString(1, LocalDateTime.now().toString());
                statement.setLong(2, recordId);
                statement.executeUpdate();
            }
            return null;
        });
    }

    public void permanentlyDeleteRecord(long recordId) {
        createSnapshot("Before permanently deleting entry " + recordId);
        transaction(() -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM records WHERE id=? AND deleted_at IS NOT NULL")) {
                statement.setLong(1, recordId);
                statement.executeUpdate();
            }
            return null;
        });
    }

    public void emptyTrash() {
        createSnapshot("Before emptying trash");
        transaction(() -> {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM records WHERE deleted_at IS NOT NULL");
            }
            return null;
        });
    }

    public FieldDefinition addField(FieldDefinition definition) {
        createSnapshot("Before adding field " + definition.name());
        return transaction(() -> {
            FieldDefinition copy = definition.copy();
            copy.setPosition(fields().size());
            long id = insertFieldInternal(copy);
            copy.setId(id);
            return copy;
        });
    }

    public void updateField(FieldDefinition definition) {
        createSnapshot("Before editing field " + definition.name());
        transaction(() -> {
            String sql = "UPDATE field_definitions SET name=?,field_type=?,position=?,required=?,default_value=?,"
                    + "min_value=?,max_value=?,unique_value=?,char_limit=?,dropdown_options=?,summary_type=? WHERE id=?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                bindField(statement, definition, false);
                statement.setLong(12, definition.id());
                statement.executeUpdate();
            }
            return null;
        });
    }

    public void deleteField(long fieldId) {
        Optional<FieldDefinition> field = fields().stream().filter(value -> value.id() == fieldId).findFirst();
        createSnapshot("Before deleting field " + field.map(FieldDefinition::name).orElse(String.valueOf(fieldId)));
        transaction(() -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM field_definitions WHERE id=?")) {
                statement.setLong(1, fieldId);
                statement.executeUpdate();
            }
            normalizeFieldPositionsInternal();
            return null;
        });
    }

    public void reorderFields(List<FieldDefinition> orderedFields) {
        createSnapshot("Before rearranging fields");
        transaction(() -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE field_definitions SET position=? WHERE id=?")) {
                for (int index = 0; index < orderedFields.size(); index++) {
                    statement.setInt(1, index);
                    statement.setLong(2, orderedFields.get(index).id());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            return null;
        });
    }

    public Map<FieldDefinition, String> summaries() {
        List<FieldDefinition> fields = fields();
        List<RecordData> records = activeRecords();
        LinkedHashMap<FieldDefinition, String> summaries = new LinkedHashMap<>();
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(Locale.getDefault());
        for (FieldDefinition field : fields) {
            if (field.summaryType() == SummaryType.NONE) {
                continue;
            }
            List<String> values = records.stream()
                    .map(record -> record.value(field.id()))
                    .filter(value -> value != null && !value.isBlank())
                    .toList();
            String display;
            if (field.summaryType() == SummaryType.COUNT) {
                display = String.valueOf(values.size());
            } else if (!field.type().isNumeric()) {
                display = "Not available";
            } else {
                List<BigDecimal> numbers = values.stream().map(value -> {
                    try {
                        return new BigDecimal(value);
                    } catch (NumberFormatException ignored) {
                        return null;
                    }
                }).filter(Objects::nonNull).toList();
                if (numbers.isEmpty()) {
                    display = field.type() == FieldType.CURRENCY ? currencyFormat.format(BigDecimal.ZERO) : "0";
                } else {
                    BigDecimal result = switch (field.summaryType()) {
                        case SUM -> numbers.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                        case AVERAGE -> numbers.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                                .divide(BigDecimal.valueOf(numbers.size()), 4, RoundingMode.HALF_UP);
                        case MINIMUM -> numbers.stream().min(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
                        case MAXIMUM -> numbers.stream().max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
                        default -> BigDecimal.ZERO;
                    };
                    display = field.type() == FieldType.CURRENCY
                            ? currencyFormat.format(result)
                            : result.stripTrailingZeros().toPlainString();
                }
            }
            summaries.put(field, display);
        }
        return summaries;
    }

    public List<String> validateValues(Map<Long, String> values, Long currentRecordId) {
        List<String> errors = new ArrayList<>();
        for (FieldDefinition field : fields()) {
            String raw = Objects.requireNonNullElse(values.get(field.id()), "").trim();
            if (field.required() && raw.isBlank()) {
                errors.add(field.name() + " is required.");
                continue;
            }
            if (raw.isBlank()) {
                continue;
            }
            if (field.characterLimit() != null && field.characterLimit() > 0
                    && raw.length() > field.characterLimit()) {
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
                        if (!field.dropdownOptions().isEmpty() && field.dropdownOptions().stream()
                                .noneMatch(option -> option.equals(raw))) {
                            errors.add(field.name() + " contains an option that is no longer available.");
                        }
                    }
                    default -> {
                    }
                }
            } catch (DateTimeParseException exception) {
                errors.add(field.name() + " contains an invalid date or time.");
            }
            if (field.uniqueValue() && valueExists(field.id(), raw, currentRecordId)) {
                errors.add(field.name() + " must be unique. This value is already in use.");
            }
        }
        return errors;
    }


    private void ensureValidValues(Map<Long, String> values, Long currentRecordId) {
        List<String> errors = validateValues(values, currentRecordId);
        if (!errors.isEmpty()) {
            throw new DatabaseException(String.join("\n", errors));
        }
    }

    public boolean integrityCheck() {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA integrity_check")) {
            return result.next() && "ok".equalsIgnoreCase(result.getString(1));
        } catch (SQLException exception) {
            throw new DatabaseException("Could not check database integrity.", exception);
        }
    }

    public void createSnapshot(String reason) {
        transaction(() -> {
            long snapshotId;
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO backup_snapshots(created_at,reason) VALUES(?,?)", Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, LocalDateTime.now().toString());
                statement.setString(2, Objects.requireNonNullElse(reason, "Automatic backup"));
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("Could not create backup snapshot.");
                    }
                    snapshotId = keys.getLong(1);
                }
            }
            copyToSnapshot(snapshotId);
            pruneSnapshotsInternal(info().backupLimit());
            return null;
        });
    }

    public List<BackupSnapshot> backupSnapshots() {
        List<BackupSnapshot> snapshots = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT id,created_at,reason FROM backup_snapshots ORDER BY id DESC")) {
            while (result.next()) {
                snapshots.add(new BackupSnapshot(result.getLong(1), LocalDateTime.parse(result.getString(2)), result.getString(3)));
            }
            return snapshots;
        } catch (SQLException exception) {
            throw new DatabaseException("Could not load database backups.", exception);
        }
    }

    public void restoreSnapshot(long snapshotId) {
        if (backupSnapshots().stream().noneMatch(snapshot -> snapshot.id() == snapshotId)) {
            throw new DatabaseException("The selected backup no longer exists.");
        }
        createSnapshot("Before restoring backup " + snapshotId);
        transaction(() -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA defer_foreign_keys=ON");
                statement.executeUpdate("DELETE FROM record_values");
                statement.executeUpdate("DELETE FROM records");
                statement.executeUpdate("DELETE FROM field_definitions");
                statement.executeUpdate("DELETE FROM pindb_meta");
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO pindb_meta(key,value) SELECT key,value FROM backup_meta WHERE snapshot_id=?")) {
                statement.setLong(1, snapshotId);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO field_definitions(id,name,field_type,position,required,default_value,min_value,max_value,"
                            + "unique_value,char_limit,dropdown_options,summary_type) "
                            + "SELECT field_id,name,field_type,position,required,default_value,min_value,max_value,"
                            + "unique_value,char_limit,dropdown_options,summary_type FROM backup_fields WHERE snapshot_id=?")) {
                statement.setLong(1, snapshotId);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO records(id,created_at,updated_at,deleted_at) "
                            + "SELECT record_id,created_at,updated_at,deleted_at FROM backup_records WHERE snapshot_id=?")) {
                statement.setLong(1, snapshotId);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO record_values(record_id,field_id,value) "
                            + "SELECT record_id,field_id,value FROM backup_values WHERE snapshot_id=?")) {
                statement.setLong(1, snapshotId);
                statement.executeUpdate();
            }
            return null;
        });
    }

    public void deleteSnapshot(long snapshotId) {
        transaction(() -> {
            deleteSnapshotInternal(snapshotId);
            return null;
        });
    }

    private void copyToSnapshot(long snapshotId) throws SQLException {
        copySnapshotTable("backup_meta", "snapshot_id,key,value",
                "SELECT ?,key,value FROM pindb_meta", snapshotId);
        copySnapshotTable("backup_fields",
                "snapshot_id,field_id,name,field_type,position,required,default_value,min_value,max_value,unique_value,char_limit,dropdown_options,summary_type",
                "SELECT ?,id,name,field_type,position,required,default_value,min_value,max_value,unique_value,char_limit,dropdown_options,summary_type FROM field_definitions",
                snapshotId);
        copySnapshotTable("backup_records", "snapshot_id,record_id,created_at,updated_at,deleted_at",
                "SELECT ?,id,created_at,updated_at,deleted_at FROM records", snapshotId);
        copySnapshotTable("backup_values", "snapshot_id,record_id,field_id,value",
                "SELECT ?,record_id,field_id,value FROM record_values", snapshotId);
    }

    private void copySnapshotTable(String table, String columns, String select, long snapshotId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO " + table + "(" + columns + ") " + select)) {
            statement.setLong(1, snapshotId);
            statement.executeUpdate();
        }
    }

    private void pruneSnapshotsInternal(int limit) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM backup_snapshots ORDER BY id DESC LIMIT -1 OFFSET ?")) {
            statement.setInt(1, Math.max(1, limit));
            try (ResultSet result = statement.executeQuery()) {
                List<Long> old = new ArrayList<>();
                while (result.next()) {
                    old.add(result.getLong(1));
                }
                for (Long id : old) {
                    deleteSnapshotInternal(id);
                }
            }
        }
    }

    private void deleteSnapshotInternal(long snapshotId) throws SQLException {
        for (String table : List.of("backup_values", "backup_records", "backup_fields", "backup_meta")) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM " + table + " WHERE snapshot_id=?")) {
                statement.setLong(1, snapshotId);
                statement.executeUpdate();
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM backup_snapshots WHERE id=?")) {
            statement.setLong(1, snapshotId);
            statement.executeUpdate();
        }
    }

    private long insertFieldInternal(FieldDefinition definition) throws SQLException {
        String sql = "INSERT INTO field_definitions(name,field_type,position,required,default_value,min_value,max_value,"
                + "unique_value,char_limit,dropdown_options,summary_type) VALUES(?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindField(statement, definition, true);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("SQLite did not return a field ID.");
                }
                return keys.getLong(1);
            }
        }
    }

    private void bindField(PreparedStatement statement, FieldDefinition field, boolean insert) throws SQLException {
        statement.setString(1, field.name());
        statement.setString(2, field.type().name());
        statement.setInt(3, field.position());
        statement.setInt(4, field.required() ? 1 : 0);
        statement.setString(5, field.defaultValue());
        statement.setString(6, field.minValue());
        statement.setString(7, field.maxValue());
        statement.setInt(8, field.uniqueValue() ? 1 : 0);
        if (field.characterLimit() == null || field.characterLimit() <= 0) {
            statement.setNull(9, Types.INTEGER);
        } else {
            statement.setInt(9, field.characterLimit());
        }
        statement.setString(10, MiniJson.stringify(field.dropdownOptions()));
        statement.setString(11, field.summaryType().name());
    }

    private FieldDefinition readField(ResultSet result) throws SQLException {
        Integer charLimit = result.getObject("char_limit") == null ? null : result.getInt("char_limit");
        List<String> options = MiniJson.array(MiniJson.parse(result.getString("dropdown_options"))).stream()
                .map(MiniJson::string).toList();
        return new FieldDefinition(
                result.getLong("id"),
                result.getString("name"),
                parseEnum(FieldType.class, result.getString("field_type"), FieldType.TEXT),
                result.getInt("position"),
                result.getInt("required") != 0,
                result.getString("default_value"),
                result.getString("min_value"),
                result.getString("max_value"),
                result.getInt("unique_value") != 0,
                charLimit,
                options,
                parseEnum(SummaryType.class, result.getString("summary_type"), SummaryType.NONE)
        );
    }

    private void writeValues(long recordId, Map<Long, String> values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO record_values(record_id,field_id,value) VALUES(?,?,?)")) {
            for (FieldDefinition field : fields()) {
                statement.setLong(1, recordId);
                statement.setLong(2, field.id());
                statement.setString(3, Objects.requireNonNullElse(values.get(field.id()), "").trim());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void normalizeFieldPositionsInternal() throws SQLException {
        List<FieldDefinition> fields = fields();
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE field_definitions SET position=? WHERE id=?")) {
            for (int index = 0; index < fields.size(); index++) {
                statement.setInt(1, index);
                statement.setLong(2, fields.get(index).id());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void validateNumber(FieldDefinition field, String raw, List<String> errors) {
        try {
            BigDecimal number = new BigDecimal(raw);
            if (!field.minValue().isBlank() && number.compareTo(new BigDecimal(field.minValue())) < 0) {
                errors.add(field.name() + " must be at least " + field.minValue() + ".");
            }
            if (!field.maxValue().isBlank() && number.compareTo(new BigDecimal(field.maxValue())) > 0) {
                errors.add(field.name() + " cannot be greater than " + field.maxValue() + ".");
            }
        } catch (NumberFormatException exception) {
            errors.add(field.name() + " must contain a valid number.");
        }
    }

    private boolean valueExists(long fieldId, String value, Long currentRecordId) {
        String sql = "SELECT 1 FROM record_values v JOIN records r ON r.id=v.record_id "
                + "WHERE v.field_id=? AND v.value=? COLLATE NOCASE AND r.deleted_at IS NULL "
                + (currentRecordId == null ? "" : "AND r.id<>? ") + "LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, fieldId);
            statement.setString(2, value);
            if (currentRecordId != null) {
                statement.setLong(3, currentRecordId);
            }
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Could not validate a unique field.", exception);
        }
    }

    private <T> T transaction(SqlSupplier<T> work) {
        try {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                T result = work.get();
                connection.commit();
                return result;
            } catch (Exception exception) {
                connection.rollback();
                if (exception instanceof DatabaseException databaseException) {
                    throw databaseException;
                }
                if (exception instanceof SQLException sqlException) {
                    throw new DatabaseException("Database operation failed.", sqlException);
                }
                throw new DatabaseException("Database operation failed.", exception);
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Could not begin or finish a database transaction.", exception);
        }
    }

    private static String resultString(ResultSet result, String column) {
        try {
            return result.getString(column);
        } catch (SQLException exception) {
            throw new DatabaseException("Could not read database entry.", exception);
        }
    }

    private static LocalDateTime nullableDateTime(String value) {
        return value == null || value.isBlank() ? null : LocalDateTime.parse(value);
    }

    private static int parseInteger(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static <T extends Enum<T>> T parseEnum(Class<T> type, String value, T fallback) {
        try {
            return Enum.valueOf(type, Objects.requireNonNullElse(value, ""));
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    @Override
    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException exception) {
                throw new DatabaseException("Could not close database cleanly.", exception);
            } finally {
                connection = null;
            }
        }
    }

    @FunctionalInterface
    private interface SqlSupplier<T> {
        T get() throws Exception;
    }

    private static final class RecordDataBuilder {
        private final long id;
        private final LocalDateTime createdAt;
        private final LocalDateTime updatedAt;
        private final LocalDateTime deletedAt;
        private final Map<Long, String> values = new LinkedHashMap<>();

        private RecordDataBuilder(long id, LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime deletedAt) {
            this.id = id;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
            this.deletedAt = deletedAt;
        }

        private RecordData build() {
            return new RecordData(id, createdAt, updatedAt, deletedAt, values);
        }
    }
}
