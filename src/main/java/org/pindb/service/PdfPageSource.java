package org.pindb.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.pindb.model.DocumentData;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class PdfPageSource implements AutoCloseable {
    private final Path temporaryFile;
    private final PDDocument document;
    private final PDFRenderer renderer;
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
        } catch (IOException | RuntimeException exception) {
            Files.deleteIfExists(temporaryFile);
            throw exception;
        }
    }

    public synchronized int pageCount() {
        ensureOpen();
        return document.getNumberOfPages();
    }

    public synchronized BufferedImage renderPage(int pageIndex, float dpi) throws IOException {
        ensureOpen();
        if (pageIndex < 0 || pageIndex >= document.getNumberOfPages()) {
            throw new IndexOutOfBoundsException("PDF page index " + pageIndex + " is out of range.");
        }
        return renderer.renderImageWithDPI(pageIndex, dpi);
    }

    @Override
    public synchronized void close() {
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

    Path temporaryFile() {
        return temporaryFile;
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("PDF page source is closed.");
        }
    }
}
