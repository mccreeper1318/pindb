package org.pindb.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InterruptedIOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentDataTest {
    @TempDir
    Path tempDirectory;

    @Test
    void streamsDocumentWithProgressWithoutChangingContent() throws Exception {
        Path file = tempDirectory.resolve("stream.txt");
        byte[] content = "streamed document".repeat(10_000).getBytes(StandardCharsets.UTF_8);
        java.nio.file.Files.write(file, content);
        AtomicLong progress = new AtomicLong();

        DocumentData document = DocumentData.read(file, "text/plain",
                (completed, total) -> progress.set(completed), () -> false);

        assertEquals(content.length, document.size());
        assertEquals(content.length, progress.get());
        assertArrayEquals(content, document.data());
    }

    @Test
    void rejectsOversizedDocumentBeforeReadingItIntoMemory() throws Exception {
        Path file = tempDirectory.resolve("oversized.bin");
        try (RandomAccessFile random = new RandomAccessFile(file.toFile(), "rw")) {
            random.setLength(DocumentData.MAX_EMBEDDED_BYTES + 1);
        }

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> DocumentData.read(file, "application/octet-stream"));

        assertTrue(failure.getMessage().contains("50 MiB"));
    }

    @Test
    void supportsCancellationDuringStreamingRead() throws Exception {
        Path file = tempDirectory.resolve("cancel.bin");
        java.nio.file.Files.write(file, new byte[256 * 1024]);

        assertThrows(InterruptedIOException.class,
                () -> DocumentData.read(file, "application/octet-stream",
                        (completed, total) -> { }, () -> true));
    }
}
