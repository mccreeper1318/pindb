package org.pindb.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pindb.model.DocumentData;

import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentCopyServiceTest {
    @TempDir
    Path tempDirectory;

    @Test
    void cancelledSavePreservesExistingDestination() throws Exception {
        Path destination = tempDirectory.resolve("existing.txt");
        byte[] original = "keep the original file".getBytes(StandardCharsets.UTF_8);
        Files.write(destination, original);

        byte[] replacement = new byte[512 * 1024];
        DocumentData document = new DocumentData("replacement.bin", "application/octet-stream", replacement);
        AtomicBoolean cancel = new AtomicBoolean();

        assertThrows(InterruptedIOException.class, () ->
                DocumentCopyService.saveAtomically(document, destination,
                        (completed, total) -> {
                            if (completed >= 64 * 1024) {
                                cancel.set(true);
                            }
                        },
                        cancel::get));

        assertArrayEquals(original, Files.readAllBytes(destination));
        try (var files = Files.list(tempDirectory)) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().endsWith(".pindb-save")));
        }
    }

    @Test
    void successfulSaveReplacesDestinationOnlyAfterCopyCompletes() throws Exception {
        Path destination = tempDirectory.resolve("existing.txt");
        Files.writeString(destination, "old");
        byte[] replacement = "new complete content".getBytes(StandardCharsets.UTF_8);
        DocumentData document = new DocumentData("replacement.txt", "text/plain", replacement);

        DocumentCopyService.saveAtomically(document, destination,
                (completed, total) -> { }, () -> false);

        assertArrayEquals(replacement, Files.readAllBytes(destination));
    }
}
