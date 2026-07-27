package org.pindb.model;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RecordData {
    private final long id;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
    private final LocalDateTime deletedAt;
    private final Map<Long, String> values;

    public RecordData(long id, LocalDateTime createdAt, LocalDateTime updatedAt,
                      LocalDateTime deletedAt, Map<Long, String> values) {
        this.id = id;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
        this.values = new LinkedHashMap<>(values);
    }

    public long id() { return id; }
    public LocalDateTime createdAt() { return createdAt; }
    public LocalDateTime updatedAt() { return updatedAt; }
    public LocalDateTime deletedAt() { return deletedAt; }
    public boolean deleted() { return deletedAt != null; }
    public Map<Long, String> values() { return Map.copyOf(values); }
    public String value(long fieldId) { return values.getOrDefault(fieldId, ""); }
}
