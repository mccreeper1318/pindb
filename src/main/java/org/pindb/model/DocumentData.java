package org.pindb.model;

import java.util.Arrays;
import java.util.Objects;

public record DocumentData(String fileName, String mimeType, byte[] data) {
    public DocumentData {
        fileName = Objects.requireNonNullElse(fileName, "document").trim();
        if (fileName.isBlank()) {
            fileName = "document";
        }
        mimeType = Objects.requireNonNullElse(mimeType, "application/octet-stream").trim();
        data = data == null ? new byte[0] : Arrays.copyOf(data, data.length);
    }

    @Override
    public byte[] data() {
        return Arrays.copyOf(data, data.length);
    }

    public long size() {
        return data.length;
    }
}
