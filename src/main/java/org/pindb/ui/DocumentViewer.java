package org.pindb.ui;

import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.print.PageLayout;
import javafx.print.PrinterJob;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.pindb.model.DocumentData;
import org.pindb.service.SettingsService;

import java.awt.Desktop;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DocumentViewer {
    private final Stage stage = new Stage();
    private final DocumentData document;
    private final VBox preview = new VBox(14);
    private final List<Image> renderedImages = new ArrayList<>();
    private String printableText;
    private boolean printable;

    public DocumentViewer(Window owner, SettingsService settings, DocumentData document) {
        this.document = document;
        stage.initOwner(owner);
        stage.initModality(Modality.NONE);
        stage.setTitle(document.fileName() + " — PinDB Document Viewer");

        preview.setPadding(new Insets(18));
        preview.setAlignment(Pos.TOP_CENTER);
        ScrollPane scroll = new ScrollPane(preview);
        scroll.setFitToWidth(true);
        scroll.setPannable(true);

        Label details = new Label(document.fileName() + "  •  " + humanSize(document.size()));
        details.getStyleClass().add("subtitle-label");
        Button print = new Button("Print…");
        Button save = new Button("Save Copy…");
        Button open = new Button("Open with System Application");
        Button close = UiUtil.primaryButton("Close");
        print.setOnAction(event -> printDocument());
        save.setOnAction(event -> saveCopy());
        open.setOnAction(event -> openExternally());
        close.setOnAction(event -> stage.close());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox toolbar = new HBox(10, details, spacer, print, save, open, close);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(12));

        BorderPane root = new BorderPane(scroll);
        root.setTop(toolbar);
        Scene scene = new Scene(root, 980, 760);
        UiUtil.applyStyles(scene, settings);
        stage.setScene(scene);
        stage.setMinWidth(720);
        stage.setMinHeight(520);

        loadPreview();
        print.setDisable(!printable);
    }

    public void show() {
        stage.show();
        stage.toFront();
    }

    private void loadPreview() {
        String extension = extension(document.fileName());
        try {
            if ("pdf".equals(extension) || "application/pdf".equalsIgnoreCase(document.mimeType())) {
                loadPdf();
            } else if ("docx".equals(extension)
                    || "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    .equalsIgnoreCase(document.mimeType())) {
                loadDocx();
            } else if (isImage(extension, document.mimeType())) {
                loadImage();
            } else if (isText(extension, document.mimeType())) {
                loadText(StandardCharsets.UTF_8);
            } else {
                Label message = new Label("PinDB has safely stored this document, but this file type does not have "
                        + "an in-app preview yet. Use Save Copy or Open with System Application.");
                message.setWrapText(true);
                message.setMaxWidth(720);
                preview.getChildren().add(message);
            }
        } catch (Exception exception) {
            preview.getChildren().clear();
            Label message = new Label("PinDB could not preview this document. The original file remains stored "
                    + "inside the database and can still be saved or opened externally.\n\n" + exception.getMessage());
            message.setWrapText(true);
            message.setMaxWidth(760);
            preview.getChildren().add(message);
            printable = false;
        }
    }

    private void loadPdf() throws IOException {
        try (PDDocument pdf = Loader.loadPDF(document.data())) {
            PDFRenderer renderer = new PDFRenderer(pdf);
            for (int page = 0; page < pdf.getNumberOfPages(); page++) {
                BufferedImage buffered = renderer.renderImageWithDPI(page, 120);
                Image image = SwingFXUtils.toFXImage(buffered, null);
                renderedImages.add(image);
                ImageView view = new ImageView(image);
                view.setPreserveRatio(true);
                view.setFitWidth(860);
                view.setSmooth(true);
                preview.getChildren().add(view);
            }
        }
        printable = !renderedImages.isEmpty();
    }

    private void loadDocx() throws IOException {
        try (XWPFDocument docx = new XWPFDocument(new ByteArrayInputStream(document.data()));
             XWPFWordExtractor extractor = new XWPFWordExtractor(docx)) {
            showText(extractor.getText());
        }
    }

    private void loadImage() {
        Image image = new Image(new ByteArrayInputStream(document.data()));
        if (image.isError()) {
            throw new IllegalArgumentException("The image data could not be decoded.");
        }
        renderedImages.add(image);
        ImageView view = new ImageView(image);
        view.setPreserveRatio(true);
        view.setFitWidth(860);
        view.setSmooth(true);
        preview.getChildren().add(view);
        printable = true;
    }

    private void loadText(Charset charset) {
        showText(new String(document.data(), charset));
    }

    private void showText(String text) {
        printableText = text == null ? "" : text;
        Label label = new Label(printableText);
        label.setWrapText(true);
        label.setMaxWidth(820);
        label.setStyle("-fx-font-family: monospace;");
        preview.getChildren().add(label);
        printable = true;
    }

    private void printDocument() {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) {
            UiUtil.warning(stage, "Printing Unavailable", "PinDB could not find a configured printer.");
            return;
        }
        if (!job.showPrintDialog(stage)) {
            return;
        }
        PageLayout layout = job.getJobSettings().getPageLayout();
        boolean success = true;
        if (!renderedImages.isEmpty()) {
            for (Image image : renderedImages) {
                ImageView page = new ImageView(image);
                page.setPreserveRatio(true);
                page.setFitWidth(layout.getPrintableWidth());
                page.setFitHeight(layout.getPrintableHeight());
                if (!job.printPage(layout, page)) {
                    success = false;
                    break;
                }
            }
        } else {
            for (String pageText : textPages(printableText)) {
                Label page = new Label(pageText);
                page.setWrapText(true);
                page.setPrefWidth(layout.getPrintableWidth());
                page.setMaxWidth(layout.getPrintableWidth());
                page.setStyle("-fx-font-family: serif; -fx-font-size: 10pt; -fx-text-fill: black; "
                        + "-fx-background-color: white; -fx-padding: 8;");
                if (!job.printPage(layout, page)) {
                    success = false;
                    break;
                }
            }
        }
        if (success) {
            job.endJob();
        } else {
            job.cancelJob();
        }
    }

    private List<String> textPages(String text) {
        String safe = text == null ? "" : text;
        int pageSize = 4_000;
        List<String> pages = new ArrayList<>();
        int start = 0;
        while (start < safe.length()) {
            int end = Math.min(safe.length(), start + pageSize);
            if (end < safe.length()) {
                int breakAt = safe.lastIndexOf('\n', end);
                if (breakAt > start + pageSize / 2) {
                    end = breakAt + 1;
                }
            }
            pages.add(safe.substring(start, end));
            start = end;
        }
        if (pages.isEmpty()) {
            pages.add("");
        }
        return pages;
    }

    private void saveCopy() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save a Copy of " + document.fileName());
        chooser.setInitialFileName(document.fileName());
        File selected = chooser.showSaveDialog(stage);
        if (selected == null) {
            return;
        }
        try {
            Files.write(selected.toPath(), document.data());
        } catch (IOException exception) {
            UiUtil.error(stage, "Could Not Save Document", "PinDB could not save the selected copy.", exception);
        }
    }

    private void openExternally() {
        if (!Desktop.isDesktopSupported()) {
            UiUtil.warning(stage, "Open Unavailable", "This system does not provide a desktop file-opening service.");
            return;
        }
        try {
            String suffix = extension(document.fileName());
            Path copy = Files.createTempFile("pindb-document-", suffix.isBlank() ? ".tmp" : "." + suffix);
            Files.write(copy, document.data());
            copy.toFile().deleteOnExit();
            Desktop.getDesktop().open(copy.toFile());
        } catch (IOException exception) {
            UiUtil.error(stage, "Could Not Open Document",
                    "PinDB could not open a temporary copy with the system application.", exception);
        }
    }

    private static boolean isImage(String extension, String mimeType) {
        return List.of("png", "jpg", "jpeg", "gif", "bmp", "webp").contains(extension)
                || mimeType.toLowerCase(Locale.ROOT).startsWith("image/");
    }

    private static boolean isText(String extension, String mimeType) {
        return List.of("txt", "md", "csv", "json", "xml", "log", "html", "htm", "rtf").contains(extension)
                || mimeType.toLowerCase(Locale.ROOT).startsWith("text/");
    }

    private static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String humanSize(long bytes) {
        if (bytes < 1_024) {
            return bytes + " B";
        }
        if (bytes < 1_048_576) {
            return String.format(Locale.US, "%.1f KB", bytes / 1_024.0);
        }
        return String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0);
    }
}
