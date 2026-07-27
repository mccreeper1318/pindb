package org.pindb.model;

import java.time.LocalDateTime;

public record BackupSnapshot(long id, LocalDateTime createdAt, String reason) {
    @Override
    public String toString() {
        return createdAt + " — " + reason;
    }
}
