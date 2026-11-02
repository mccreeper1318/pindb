package org.pindb.service;

import org.pindb.model.DocumentData;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.function.BooleanSupplier;

public final class DocumentCopyService {
    private DocumentCopyService() {
    }

    public static void saveAtomically(DocumentData document, Path destination,
                                      DocumentData.ProgressListener progress,
                                      BooleanSupplier cancelled) throws IOException {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(cancelled, "cancelled");

        Path absolute = destination.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent == null) {
            throw new IOException("The selected destination does not have a parent directory.");
        }
        Files.createDirectories(parent);

        String name = absolute.getFileName().toString();
        Path temporary = Files.createTempFile(parent, "." + name + ".", ".pindb-save");
        boolean published = false;
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) {
                document.writeTo(output, progress, cancelled);
            }
            if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) {
                throw new InterruptedIOException("Document saving was cancelled.");
            }
            replaceCompletedCopy(temporary, absolute);
            published = true;
        } finally {
            if (!published) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private static void replaceCompletedCopy(Path temporary, Path destination) throws IOException {
        try {
            Files.move(temporary, destination,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
