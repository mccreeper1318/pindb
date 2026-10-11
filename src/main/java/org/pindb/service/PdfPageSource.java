package org.pindb.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.pindb.model.DocumentData;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

public final class PdfPageSource implements AutoCloseable {
    private final Path temporaryFile;
    private final PDDocument document;
    private final PDFRenderer renderer;
    private final int pageCount;
    private final ReentrantLock access = new ReentrantLock();
    private volatile boolean closeRequested;
    private boolean closed;

    public PdfPageSource(DocumentData data) throws IOException {
        Objects.requireNonNull(data, "data");
        temporaryFile = Files.createTempFile("pindb-pdf-preview-", ".pdf");
        try {
            try (OutputStream output = Files.newOutputStream(temporaryFile)) {
                data.writeTo(output);
            }
            document = Loader.loadPDF(temporaryFile.toFile());
            renderer = new PDFRenderer(document);
            pageCount = document.getNumberOfPages();
        } catch (IOException | RuntimeException exception) {
            Files.deleteIfExists(temporaryFile);
            throw exception;
        }
    }

    public int pageCount() {
        return pageCount;
    }

    public BufferedImage renderPage(int pageIndex, float dpi) throws IOException {
        try {
            access.lockInterruptibly();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("PDF rendering was cancelled.");
        }
        try {
            ensureOpen();
            if (pageIndex < 0 || pageIndex >= pageCount) {
                throw new IndexOutOfBoundsException("PDF page index " + pageIndex + " is out of range.");
            }
            return renderer.renderImageWithDPI(pageIndex, dpi);
        } finally {
            finishCloseIfRequested();
            access.unlock();
        }
    }

    @Override
    public void close() {
        closeRequested = true;
        if (!access.tryLock()) {
            return;
        }
        try {
            closeInternal();
        } finally {
            access.unlock();
        }
    }

    Path temporaryFile() {
        return temporaryFile;
    }

    boolean closeRequested() {
        return closeRequested;
    }

    private void finishCloseIfRequested() {
        if (closeRequested) {
            closeInternal();
        }
    }

    private void closeInternal() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            document.close();
        } catch (IOException ignored) {
            // Best effort cleanup; the source document remains safely stored in PinDB.
        }
        try {
            Files.deleteIfExists(temporaryFile);
        } catch (IOException ignored) {
            temporaryFile.toFile().deleteOnExit();
        }
    }

    private void ensureOpen() {
        if (closed || closeRequested) {
            throw new IllegalStateException("PDF page source is closed.");
        }
    }
}
