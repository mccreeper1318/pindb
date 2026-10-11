package org.pindb.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.pindb.model.DocumentData;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfPageSourceTest {
    @Test
    void opensMultipagePdfAndRendersPagesIndividually() throws Exception {
        byte[] pdfBytes;
        try (PDDocument pdf = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (int page = 0; page < 12; page++) {
                pdf.addPage(new PDPage());
            }
            pdf.save(output);
            pdfBytes = output.toByteArray();
        }

        DocumentData data = new DocumentData("multipage.pdf", "application/pdf", pdfBytes);
        Path temporary;
        try (PdfPageSource source = new PdfPageSource(data)) {
            temporary = source.temporaryFile();
            assertEquals(12, source.pageCount());
            assertTrue(Files.exists(temporary));
            assertTrue(source.renderPage(0, 72).getWidth() > 0);
            assertTrue(source.renderPage(11, 72).getHeight() > 0);
        }
        assertFalse(Files.exists(temporary));
    }
}
