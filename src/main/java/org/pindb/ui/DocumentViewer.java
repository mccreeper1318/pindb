package org.pindb.ui;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.print.PageLayout;
import javafx.print.PrinterJob;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.pindb.model.DocumentData;
import org.pindb.service.PdfPageSource;
import org.pindb.service.SettingsService;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class DocumentViewer {
    private static final int PDF_CACHE_PAGES = 5;
    private static final float PDF_PREVIEW_DPI = 120f;
    private static final float PDF_PRINT_DPI = 150f;

    private final Stage stage = new Stage();
    private final DocumentData document;
    private final StackPane previewHost = new StackPane();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(
            Thread.ofVirtual().name("pindb-document-viewer-", 0).factory());
    private final Map<Integer, Image> pdfCache = new LinkedHashMap<>(8, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, Image> eldest) {
            return size() > PDF_CACHE_PAGES;
        }
    };
    private PdfPageSource pdfSource;
    private Task<?> currentPreviewTask;
    private String printableText;
    private Image printableImage;
    private boolean printable;

    public DocumentViewer(Window owner, SettingsService settings, DocumentData document) {
        this.document = document;
        stage.initOwner(owner);
        stage.initModality(Modality.NONE);
        stage.setTitle(document.fileName() + " — PinDB Document Viewer");

        Label details = new Label(document.fileName() + "  •  " + humanSize(document.size()));
        details.getStyleClass().add("subtitle-label");
        Button print = new Button("Print…");
        Button save = new Button("Save Copy…");
        Button open = new Button("Open with System Application");
        Button close = UiUtil.primaryButton("Close");
        print.setDisable(true);
        print.setOnAction(event -> printDocument());
        save.setOnAction(event -> saveCopy());
        open.setOnAction(event -> openExternally());
        close.setOnAction(event -> stage.close());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox toolbar = new HBox(10, details, spacer, print, save, open, close);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(12));

        BorderPane root = new BorderPane(previewHost);
        root.setTop(toolbar);
        Scene scene = new Scene(root, 980, 760);
        UiUtil.applyStyles(scene, settings);
        stage.setScene(scene);
        stage.setMinWidth(720);
        stage.setMinHeight(520);
        stage.setOnHidden(event -> closeResources());

        loadPreview(print);
    }

    public void show() {
        stage.show();
        stage.toFront();
    }

    private void loadPreview(Button printButton) {
        String extension = extension(document.fileName());
        if ("pdf".equals(extension) || "application/pdf".equalsIgnoreCase(document.mimeType())) {
            loadPdf(printButton);
        } else if ("docx".equals(extension)
                || "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                .equalsIgnoreCase(document.mimeType())) {
            loadDocx(printButton);
        } else if (isImage(extension, document.mimeType())) {
            loadImage(printButton);
        } else if (isText(extension, document.mimeType())) {
            loadText(StandardCharsets.UTF_8, printButton);
        } else {
            Label message = new Label("PinDB has safely stored this document, but this file type does not have "
                    + "an in-app preview yet. Use Save Copy or Open with System Application.");
            message.setWrapText(true);
            message.setMaxWidth(720);
            previewHost.getChildren().setAll(message);
        }
    }

    private void loadPdf(Button printButton) {
        Task<PdfPageSource> task = new Task<>() {
            @Override
            protected PdfPageSource call() throws Exception {
                return new PdfPageSource(document);
            }
        };
        showWorking("Preparing PDF preview…", task);
        task.setOnSucceeded(event -> {
            if (currentPreviewTask == task) {
                currentPreviewTask = null;
            }
            pdfSource = task.getValue();
            int pageCount = pdfSource.pageCount();
            showPdfPages(pageCount);
            printable = pageCount > 0;
            printButton.setDisable(!printable);
        });
        task.setOnFailed(event -> showPreviewFailure(task.getException(), printButton));
        task.setOnCancelled(event -> showCancelledPreview(printButton));
        worker.submit(task);
    }

    private void showPdfPages(int pageCount) {
        ListView<Integer> pages = new ListView<>(FXCollections.observableArrayList(
                IntStream.range(0, pageCount).boxed().toList()));
        pages.setCellFactory(ignored -> new PdfPageCell());
        pages.setFixedCellSize(-1);
        pages.setPlaceholder(new Label("This PDF does not contain any pages."));
        previewHost.getChildren().setAll(pages);
    }

    private void loadDocx(Button printButton) {
        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                try (XWPFDocument docx = new XWPFDocument(document.openStream());
                     XWPFWordExtractor extractor = new XWPFWordExtractor(docx)) {
                    return extractor.getText();
                }
            }
        };
        loadTextTask(task, "Reading document…", printButton);
    }

    private void loadImage(Button printButton) {
        Task<Image> task = new Task<>() {
            @Override
            protected Image call() {
                Image image = new Image(document.openStream());
                if (image.isError()) {
                    throw new IllegalArgumentException("The image data could not be decoded.");
                }
                return image;
            }
        };
        showWorking("Decoding image…", task);
        task.setOnSucceeded(event -> {
            currentPreviewTask = null;
            printableImage = task.getValue();
            showImage(printableImage);
            printable = true;
            printButton.setDisable(false);
        });
        task.setOnFailed(event -> showPreviewFailure(task.getException(), printButton));
        task.setOnCancelled(event -> showCancelledPreview(printButton));
        worker.submit(task);
    }

    private void loadText(Charset charset, Button printButton) {
        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(document.openStream(), charset))) {
                    return reader.lines().collect(Collectors.joining(System.lineSeparator()));
                }
            }
        };
        loadTextTask(task, "Reading text…", printButton);
    }

    private void loadTextTask(Task<String> task, String message, Button printButton) {
        showWorking(message, task);
        task.setOnSucceeded(event -> {
            currentPreviewTask = null;
            showText(task.getValue());
            printable = true;
            printButton.setDisable(false);
        });
        task.setOnFailed(event -> showPreviewFailure(task.getException(), printButton));
        task.setOnCancelled(event -> showCancelledPreview(printButton));
        worker.submit(task);
    }

    private void showWorking(String message, Task<?> task) {
        currentPreviewTask = task;
        ProgressIndicator progress = new ProgressIndicator();
        Button cancel = new Button("Cancel");
        cancel.setOnAction(event -> task.cancel(true));
        VBox box = new VBox(12, new Label(message), progress, cancel);
        box.setAlignment(Pos.CENTER);
        previewHost.getChildren().setAll(box);
    }

    private void showPreviewFailure(Throwable exception, Button printButton) {
        currentPreviewTask = null;
        printable = false;
        printButton.setDisable(true);
        Label message = new Label("PinDB could not preview this document. The original file remains stored "
                + "inside the database and can still be saved or opened externally.\n\n"
                + (exception == null ? "Unknown preview error." : exception.getMessage()));
        message.setWrapText(true);
        message.setMaxWidth(760);
        previewHost.getChildren().setAll(message);
    }

    private void showCancelledPreview(Button printButton) {
        currentPreviewTask = null;
        printable = false;
        printButton.setDisable(true);
        previewHost.getChildren().setAll(new Label("Preview loading cancelled."));
    }

    private void showImage(Image image) {
        ImageView view = imageView(image, 860);
        ScrollPane scroll = new ScrollPane(view);
        scroll.setFitToWidth(true);
        scroll.setPannable(true);
        previewHost.getChildren().setAll(scroll);
    }

    private void showText(String text) {
        printableText = text == null ? "" : text;
        Label label = new Label(printableText);
        label.setWrapText(true);
        label.setMaxWidth(820);
        label.setStyle("-fx-font-family: monospace;");
        VBox box = new VBox(label);
        box.setPadding(new Insets(18));
        box.setAlignment(Pos.TOP_CENTER);
        ScrollPane scroll = new ScrollPane(box);
        scroll.setFitToWidth(true);
        previewHost.getChildren().setAll(scroll);
    }

    private void printDocument() {
        if (!printable) {
            return;
        }
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) {
            UiUtil.warning(stage, "Printing Unavailable", "PinDB could not find a configured printer.");
            return;
        }
        if (!job.showPrintDialog(stage)) {
            return;
        }
        PageLayout layout = job.getJobSettings().getPageLayout();
        if (pdfSource != null) {
            printPdf(job, layout);
            return;
        }

        boolean success = true;
        if (printableImage != null) {
            ImageView page = imageView(printableImage, layout.getPrintableWidth());
            page.setFitHeight(layout.getPrintableHeight());
            success = job.printPage(layout, page);
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

    private void printPdf(PrinterJob job, PageLayout layout) {
        int total = pdfSource.pageCount();
        Stage progressStage = new Stage();
        progressStage.initOwner(stage);
        progressStage.initModality(Modality.WINDOW_MODAL);
        progressStage.setTitle("Printing " + document.fileName());
        Label status = new Label("Preparing page 1 of " + total + "…");
        ProgressBar progress = new ProgressBar(0);
        progress.setPrefWidth(320);
        Button cancel = new Button("Cancel");
        VBox content = new VBox(12, status, progress, cancel);
        content.setPadding(new Insets(18));
        content.setAlignment(Pos.CENTER);
        progressStage.setScene(new Scene(content));
        progressStage.setResizable(false);

        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicReference<Task<Image>> activeTask = new AtomicReference<>();
        cancel.setOnAction(event -> {
            cancelled.set(true);
            Task<Image> task = activeTask.get();
            if (task != null) {
                task.cancel(true);
            }
            job.cancelJob();
            progressStage.close();
        });
        progressStage.setOnCloseRequest(event -> {
            event.consume();
            cancel.fire();
        });
        progressStage.show();
        printPdfPage(job, layout, 0, total, status, progress, progressStage, cancelled, activeTask);
    }

    private void printPdfPage(PrinterJob job, PageLayout layout, int pageIndex, int total,
                              Label status, ProgressBar progress, Stage progressStage,
                              AtomicBoolean cancelled, AtomicReference<Task<Image>> activeTask) {
        if (cancelled.get()) {
            return;
        }
        if (pageIndex >= total) {
            boolean success = job.endJob();
            progressStage.close();
            if (!success) {
                UiUtil.warning(stage, "Printing Unsuccessful", "The printer did not complete the PDF print job.");
            }
            return;
        }

        status.setText("Preparing page " + (pageIndex + 1) + " of " + total + "…");
        progress.setProgress(pageIndex / (double) total);
        Task<Image> task = new Task<>() {
            @Override
            protected Image call() throws Exception {
                return SwingFXUtils.toFXImage(pdfSource.renderPage(pageIndex, PDF_PRINT_DPI), null);
            }
        };
        activeTask.set(task);
        task.setOnSucceeded(event -> {
            if (cancelled.get()) {
                return;
            }
            ImageView page = imageView(task.getValue(), layout.getPrintableWidth());
            page.setFitHeight(layout.getPrintableHeight());
            if (!job.printPage(layout, page)) {
                job.cancelJob();
                progressStage.close();
                UiUtil.warning(stage, "Printing Unsuccessful", "The printer could not print page "
                        + (pageIndex + 1) + ".");
                return;
            }
            progress.setProgress((pageIndex + 1) / (double) total);
            Platform.runLater(() -> printPdfPage(job, layout, pageIndex + 1, total, status, progress,
                    progressStage, cancelled, activeTask));
        });
        task.setOnFailed(event -> {
            job.cancelJob();
            progressStage.close();
            UiUtil.error(stage, "Printing Failed", "PinDB could not render PDF page "
                    + (pageIndex + 1) + " for printing.", task.getException());
        });
        task.setOnCancelled(event -> {
            if (!cancelled.get()) {
                job.cancelJob();
                progressStage.close();
            }
        });
        worker.submit(task);
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
        runFileWrite("Saving document…", selected.toPath(), false);
    }

    private void openExternally() {
        if (!Desktop.isDesktopSupported()) {
            UiUtil.warning(stage, "Open Unavailable", "This system does not provide a desktop file-opening service.");
            return;
        }
        try {
            String suffix = extension(document.fileName());
            Path copy = Files.createTempFile("pindb-document-", suffix.isBlank() ? ".tmp" : "." + suffix);
            runFileWrite("Preparing system copy…", copy, true);
        } catch (IOException exception) {
            UiUtil.error(stage, "Could Not Open Document",
                    "PinDB could not prepare a temporary copy for the system application.", exception);
        }
    }

    private void runFileWrite(String message, Path destination, boolean openAfter) {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                try (var output = Files.newOutputStream(destination)) {
                    document.writeTo(output,
                            (completed, total) -> updateProgress(completed, total),
                            this::isCancelled);
                }
                return null;
            }
        };
        Stage progressStage = new Stage();
        progressStage.initOwner(stage);
        progressStage.initModality(Modality.WINDOW_MODAL);
        progressStage.setTitle(message);
        ProgressBar indicator = new ProgressBar(0);
        indicator.setPrefWidth(280);
        indicator.progressProperty().bind(task.progressProperty());
        Button cancel = new Button("Cancel");
        cancel.setOnAction(event -> task.cancel(true));
        VBox box = new VBox(12, new Label(message), indicator, cancel);
        box.setPadding(new Insets(18));
        box.setAlignment(Pos.CENTER);
        progressStage.setScene(new Scene(box));
        task.setOnSucceeded(event -> {
            progressStage.close();
            if (openAfter) {
                destination.toFile().deleteOnExit();
                try {
                    Desktop.getDesktop().open(destination.toFile());
                } catch (IOException exception) {
                    UiUtil.error(stage, "Could Not Open Document",
                            "PinDB could not open the temporary copy with the system application.", exception);
                }
            }
        });
        task.setOnFailed(event -> {
            progressStage.close();
            UiUtil.error(stage, openAfter ? "Could Not Open Document" : "Could Not Save Document",
                    "PinDB could not write the selected document copy.", task.getException());
        });
        task.setOnCancelled(event -> {
            progressStage.close();
            if (openAfter) {
                try {
                    Files.deleteIfExists(destination);
                } catch (IOException ignored) {
                    destination.toFile().deleteOnExit();
                }
            }
        });
        progressStage.setOnCloseRequest(event -> {
            event.consume();
            task.cancel(true);
        });
        progressStage.show();
        worker.submit(task);
    }

    private void closeResources() {
        Task<?> task = currentPreviewTask;
        if (task != null) {
            task.cancel(true);
        }
        worker.shutdownNow();
        if (pdfSource != null) {
            pdfSource.close();
            pdfSource = null;
        }
        synchronized (pdfCache) {
            pdfCache.clear();
        }
    }

    private Image cachedPdfPage(int pageIndex) {
        synchronized (pdfCache) {
            return pdfCache.get(pageIndex);
        }
    }

    private void cachePdfPage(int pageIndex, Image image) {
        synchronized (pdfCache) {
            pdfCache.put(pageIndex, image);
        }
    }

    private static ImageView imageView(Image image, double fitWidth) {
        ImageView view = new ImageView(image);
        view.setPreserveRatio(true);
        view.setFitWidth(fitWidth);
        view.setSmooth(true);
        return view;
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

    private final class PdfPageCell extends ListCell<Integer> {
        private Task<Image> renderTask;

        @Override
        protected void updateItem(Integer pageIndex, boolean empty) {
            super.updateItem(pageIndex, empty);
            if (renderTask != null) {
                renderTask.cancel(true);
                renderTask = null;
            }
            setText(null);
            setGraphic(null);
            if (empty || pageIndex == null || pdfSource == null) {
                return;
            }

            Image cached = cachedPdfPage(pageIndex);
            if (cached != null) {
                setGraphic(pageCard(pageIndex, cached));
                return;
            }

            ProgressIndicator progress = new ProgressIndicator();
            progress.setMaxSize(40, 40);
            Label label = new Label("Rendering page " + (pageIndex + 1) + "…");
            VBox loading = new VBox(8, label, progress);
            loading.setAlignment(Pos.CENTER);
            loading.setPadding(new Insets(20));
            setGraphic(loading);

            Task<Image> task = new Task<>() {
                @Override
                protected Image call() throws Exception {
                    return SwingFXUtils.toFXImage(pdfSource.renderPage(pageIndex, PDF_PREVIEW_DPI), null);
                }
            };
            renderTask = task;
            task.setOnSucceeded(event -> {
                cachePdfPage(pageIndex, task.getValue());
                if (!isEmpty() && pageIndex.equals(getItem())) {
                    setGraphic(pageCard(pageIndex, task.getValue()));
                }
                if (renderTask == task) {
                    renderTask = null;
                }
            });
            task.setOnFailed(event -> {
                if (!isEmpty() && pageIndex.equals(getItem())) {
                    Label failure = new Label("Could not render page " + (pageIndex + 1) + ".");
                    failure.getStyleClass().add("error-label");
                    setGraphic(failure);
                }
                if (renderTask == task) {
                    renderTask = null;
                }
            });
            worker.submit(task);
        }

        private VBox pageCard(int pageIndex, Image image) {
            Label label = new Label("Page " + (pageIndex + 1));
            label.getStyleClass().add("muted-label");
            ImageView view = imageView(image, 860);
            VBox card = new VBox(8, label, view);
            card.setAlignment(Pos.TOP_CENTER);
            card.setPadding(new Insets(12));
            return card;
        }
    }
}
