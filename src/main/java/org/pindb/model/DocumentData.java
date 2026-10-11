package org.pindb.model;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.BooleanSupplier;

public final class DocumentData {
    public static final long MAX_EMBEDDED_BYTES = 50L * 1024L * 1024L;
    public static final int MAX_EMBEDDED_MIB = 50;
    private static final int COPY_BUFFER_SIZE = 64 * 1024;

    private final String fileName;
    private final String mimeType;
    private final byte[] data;

    public DocumentData(String fileName, String mimeType, byte[] data) {
        this(fileName, mimeType, data, false);
    }

    private DocumentData(String fileName, String mimeType, byte[] data, boolean trustedArray) {
        this.fileName = normalizeFileName(fileName);
        this.mimeType = normalizeMimeType(mimeType);
        byte[] safe = data == null ? new byte[0] : data;
        requireSupportedSize(safe.length);
        this.data = trustedArray ? safe : Arrays.copyOf(safe, safe.length);
    }

    public static DocumentData read(Path path, String mimeType) throws IOException {
        return read(path, mimeType, (completed, total) -> { }, () -> false);
    }

    public static DocumentData read(Path path, String mimeType, ProgressListener progress,
                                    BooleanSupplier cancelled) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(cancelled, "cancelled");

        long expectedSize = Files.size(path);
        requireSupportedSize(expectedSize);
        int initialCapacity = (int) Math.min(expectedSize, MAX_EMBEDDED_BYTES);
        ByteArrayOutputStream output = new ByteArrayOutputStream(initialCapacity);
        long totalRead = 0;
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[COPY_BUFFER_SIZE];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) {
                    throw new InterruptedIOException("Document loading was cancelled.");
                }
                totalRead += count;
                requireSupportedSize(totalRead);
                output.write(buffer, 0, count);
                progress.update(totalRead, Math.max(expectedSize, totalRead));
            }
        }
        return new DocumentData(path.getFileName().toString(), mimeType, output.toByteArray(), true);
    }

    public String fileName() {
        return fileName;
    }

    public String mimeType() {
        return mimeType;
    }

    public byte[] data() {
        return Arrays.copyOf(data, data.length);
    }

    public InputStream openStream() {
        return new ByteArrayInputStream(data);
    }

    public void writeTo(OutputStream output) throws IOException {
        Objects.requireNonNull(output, "output").write(data);
    }

    public void writeTo(Path destination) throws IOException {
        try (OutputStream output = Files.newOutputStream(destination)) {
            writeTo(output);
        }
    }

    public long size() {
        return data.length;
    }

    public boolean isEmpty() {
        return data.length == 0;
    }

    public static void requireSupportedSize(long size) {
        if (size < 0 || size > MAX_EMBEDDED_BYTES) {
            throw new IllegalArgumentException("Embedded documents are limited to "
                    + MAX_EMBEDDED_MIB + " MiB per file.");
        }
    }

    private static String normalizeFileName(String value) {
        String normalized = Objects.requireNonNullElse(value, "document").trim();
        return normalized.isBlank() ? "document" : normalized;
    }

    private static String normalizeMimeType(String value) {
        String normalized = Objects.requireNonNullElse(value, "application/octet-stream").trim();
        return normalized.isBlank() ? "application/octet-stream" : normalized;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof DocumentData that
                && fileName.equals(that.fileName)
                && mimeType.equals(that.mimeType)
                && Arrays.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(fileName, mimeType);
        return 31 * result + Arrays.hashCode(data);
    }

    @FunctionalInterface
    public interface ProgressListener {
        void update(long completedBytes, long totalBytes);
    }
}
