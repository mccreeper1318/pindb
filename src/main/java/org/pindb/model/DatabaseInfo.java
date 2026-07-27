package org.pindb.model;

import java.nio.file.Path;

public record DatabaseInfo(
        Path path,
        String name,
        String description,
        int schemaVersion,
        DatabaseView defaultView,
        boolean suppressDeleteConfirmation,
        int backupLimit
) {
}
