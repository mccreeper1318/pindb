package org.pindb.service;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.print.PageLayout;
import javafx.print.PageOrientation;
import javafx.print.Printer;
import javafx.print.PrinterJob;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.transform.Scale;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.pindb.model.DatabaseInfo;
import org.pindb.model.FieldDefinition;
import org.pindb.model.FieldType;
import org.pindb.model.PrintArrangement;
import org.pindb.model.PrintOptions;
import org.pindb.model.RecordData;
import org.pindb.model.SummaryType;
import org.pindb.ui.UiUtil;

import javax.print.PrintServiceLookup;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class PrintService {
    private static final DateTimeFormatter PRINT_DATE = DateTimeFormatter.ofPattern("MMM d, uuuu h:mm a");
    private static final double HEADER_RESERVE = 38;
    private static final double FOOTER_RESERVE = 30;
    private static final double PAGE_PADDING = 12;
    private static final double CELL_BASE_HEIGHT = 25;
    private static final double TEXT_LINE_HEIGHT = 15;
    private static final double FALLBACK_RENDER_SCALE = 2.0;

    private PrintService() {
    }

    public static boolean print(Window owner, DatabaseInfo info, List<FieldDefinition> allFields,
                                List<RecordData> records, PrintOptions options) {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) {
            return printWithDesktopFallback(owner, info, allFields, records, options);
        }
        if (!job.showPrintDialog(owner)) {
            return false;
        }

        Printer printer = job.getPrinter();
        PageLayout selectedLayout = job.getJobSettings().getPageLayout();
        PageOrientation orientation = options.landscape() ? PageOrientation.LANDSCAPE : PageOrientation.PORTRAIT;
        PageLayout pageLayout = printer.createPageLayout(
                selectedLayout.getPaper(), orientation, Printer.MarginType.DEFAULT);
        job.getJobSettings().setPageLayout(pageLayout);

        PrintArea area = new PrintArea(pageLayout.getPrintableWidth(), pageLayout.getPrintableHeight());
        List<Node> pages = printablePages(info, allFields, records, options, area);
        boolean success = true;
        for (Node printablePage : pages) {
            printablePage.applyCss();
            printablePage.autosize();
            if (!job.printPage(pageLayout, printablePage)) {
                success = false;
                break;
            }
        }
        if (success) {
            success = job.endJob();
        } else {
            job.cancelJob();
        }
        return success;
    }

    private static boolean printWithDesktopFallback(Window owner, DatabaseInfo info,
                                                    List<FieldDefinition> allFields,
                                                    List<RecordData> records, PrintOptions options) {
        javax.print.PrintService[] services;
        try {
            services = PrintServiceLookup.lookupPrintServices(null, null);
        } catch (RuntimeException exception) {
            UiUtil.error(owner, "Printing Unavailable",
                    "JavaFX could not find a printer, and PinDB could not query the system print service.", exception);
            return false;
        }

        if (services.length == 0) {
            UiUtil.warning(owner, "Printing Unavailable",
                    "Neither JavaFX nor the system Java/CUPS print service could find a configured printer. "
                            + "Check that the printer is enabled in system settings and that the CUPS service is running.");
            return false;
        }

        try {
            java.awt.print.PrinterJob awtJob = java.awt.print.PrinterJob.getPrinterJob();
            if (awtJob.getPrintService() == null) {
                awtJob.setPrintService(services[0]);
            }
            if (!awtJob.printDialog()) {
                return false;
            }

            PageFormat pageFormat = awtJob.defaultPage();
            pageFormat.setOrientation(options.landscape() ? PageFormat.LANDSCAPE : PageFormat.PORTRAIT);
            pageFormat = awtJob.validatePage(pageFormat);
            PrintArea area = new PrintArea(pageFormat.getImageableWidth(), pageFormat.getImageableHeight());
            List<Node> pages = printablePages(info, allFields, records, options, area);
            OnDemandPageSource<Node, BufferedImage> renderedPages =
                    new OnDemandPageSource<>(pages, PrintService::renderFallbackPageOnFxThread);
            PageFormat finalPageFormat = pageFormat;

            Stage progressStage = new Stage();
            progressStage.initOwner(owner);
            progressStage.initModality(Modality.WINDOW_MODAL);
            progressStage.setTitle("Printing " + info.name());
            Label status = new Label("Preparing print job…");
            ProgressBar progress = new ProgressBar(0);
            progress.setPrefWidth(320);
            Button cancel = new Button("Cancel");
            VBox progressBox = new VBox(12, status, progress, cancel);
            progressBox.setPadding(new Insets(18));
            progressBox.setAlignment(Pos.CENTER);
            progressStage.setScene(new Scene(progressBox));
            progressStage.setResizable(false);

            AtomicBoolean cancelled = new AtomicBoolean();
            AtomicBoolean completed = new AtomicBoolean();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            awtJob.setPrintable((graphics, format, pageIndex) -> {
                if (cancelled.get()) {
                    return Printable.NO_SUCH_PAGE;
                }
                if (pageIndex < 0 || pageIndex >= renderedPages.size()) {
                    return Printable.NO_SUCH_PAGE;
                }
                BufferedImage image = renderedPages.render(pageIndex);
                Graphics2D graphics2D = (Graphics2D) graphics.create();
                try {
                    graphics2D.translate(finalPageFormat.getImageableX(), finalPageFormat.getImageableY());
                    graphics2D.drawImage(image,
                            0, 0,
                            (int) Math.round(finalPageFormat.getImageableWidth()),
                            (int) Math.round(finalPageFormat.getImageableHeight()),
                            null);
                } finally {
                    graphics2D.dispose();
                }
                int completedPages = pageIndex + 1;
                Platform.runLater(() -> {
                    status.setText("Printing page " + completedPages + " of " + renderedPages.size() + "…");
                    progress.setProgress(completedPages / (double) renderedPages.size());
                });
                return Printable.PAGE_EXISTS;
            }, finalPageFormat);

            Task<Void> printTask = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    awtJob.print();
                    return null;
                }
            };
            printTask.setOnSucceeded(event -> {
                completed.set(true);
                progressStage.close();
            });
            printTask.setOnFailed(event -> {
                failure.set(printTask.getException());
                progressStage.close();
            });
            printTask.setOnCancelled(event -> progressStage.close());
            cancel.setOnAction(event -> {
                cancelled.set(true);
                awtJob.cancel();
                printTask.cancel(true);
            });
            progressStage.setOnCloseRequest(event -> {
                event.consume();
                cancel.fire();
            });

            Thread.ofVirtual().name("pindb-print-fallback").start(printTask);
            progressStage.showAndWait();

            if (failure.get() != null) {
                UiUtil.error(owner, "Printing Failed",
                        "The system printer was detected, but PinDB could not submit the print job.", failure.get());
                return false;
            }
            return completed.get() && !cancelled.get();
        } catch (PrinterException | RuntimeException exception) {
            UiUtil.error(owner, "Printing Failed",
                    "The system printer was detected, but PinDB could not submit the print job.", exception);
            return false;
        }
    }

    private static BufferedImage renderFallbackPageOnFxThread(Node page) {
        if (Platform.isFxApplicationThread()) {
            return renderFallbackPage(page);
        }

        CountDownLatch ready = new CountDownLatch(1);
        AtomicReference<BufferedImage> image = new AtomicReference<>();
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                image.set(renderFallbackPage(page));
            } catch (RuntimeException exception) {
                failure.set(exception);
            } finally {
                ready.countDown();
            }
        });
        try {
            ready.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Printing was interrupted while rendering a page.", exception);
        }
        if (failure.get() != null) {
            throw failure.get();
        }
        return image.get();
    }

    private static BufferedImage renderFallbackPage(Node page) {
        page.applyCss();
        page.autosize();
        SnapshotParameters parameters = new SnapshotParameters();
        parameters.setTransform(new Scale(FALLBACK_RENDER_SCALE, FALLBACK_RENDER_SCALE));
        WritableImage image = page.snapshot(parameters, null);
        return SwingFXUtils.fromFXImage(image, null);
    }

    private static List<Node> printablePages(DatabaseInfo info, List<FieldDefinition> allFields,
                                             List<RecordData> records, PrintOptions options, PrintArea area) {
        List<FieldDefinition> fields = selectedFields(allFields, options.fieldIds());
        List<Node> bodies = options.arrangement() == PrintArrangement.COLUMNS
                ? columnBodies(fields, records, options, area)
                : rowBodies(fields, records, options, area);
        if (options.includeSummaries()) {
            appendSummaryBody(bodies, fields, records, options, area);
        }
        if (bodies.isEmpty()) {
            bodies.add(new Label("No entries in this database."));
        }

        int pageCount = bodies.size();
        List<Node> pages = new ArrayList<>(pageCount);
        for (int index = 0; index < pageCount; index++) {
            pages.add(page(info, bodies.get(index), options, index + 1, pageCount, area));
        }
        return pages;
    }

    private static List<FieldDefinition> selectedFields(List<FieldDefinition> all, List<Long> selectedIds) {
        Map<Long, FieldDefinition> byId = new LinkedHashMap<>();
        all.forEach(field -> byId.put(field.id(), field));
        return selectedIds.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    private static List<Node> columnBodies(List<FieldDefinition> fields, List<RecordData> records,
                                           PrintOptions options, PrintArea area) {
        List<Node> pages = new ArrayList<>();
        double bodyHeight = availableBodyHeight(options, area);
        double columnWidth = printableColumnWidth(area, fields.size());
        int index = 0;
        boolean firstPage = true;
        while (index < records.size() || (records.isEmpty() && firstPage)) {
            GridPane grid = tableGrid(fields, columnWidth);
            int row = 0;
            double used = 0;
            boolean headings = options.repeatHeadings() || firstPage;
            if (headings) {
                addHeadings(grid, fields, row++);
                used += estimateHeadingHeight(fields, columnWidth);
            }
            if (records.isEmpty()) {
                grid.add(cell("No entries in this database.", false), 0, row, Math.max(1, fields.size()), 1);
                pages.add(grid);
                break;
            }
            while (index < records.size()) {
                RecordData record = records.get(index);
                double rowHeight = estimateRecordRowHeight(fields, record, columnWidth);
                if (row > (headings ? 1 : 0) && used + rowHeight > bodyHeight) {
                    break;
                }
                for (int col = 0; col < fields.size(); col++) {
                    FieldDefinition field = fields.get(col);
                    grid.add(cell(UiUtil.formatValue(field, record.value(field.id())), false), col, row);
                }
                row++;
                used += Math.min(rowHeight, bodyHeight);
                index++;
                if (used >= bodyHeight) {
                    break;
                }
            }
            pages.add(grid);
            firstPage = false;
        }
        return pages;
    }

    private static List<Node> rowBodies(List<FieldDefinition> fields, List<RecordData> records,
                                        PrintOptions options, PrintArea area) {
        List<Node> pages = new ArrayList<>();
        double bodyHeight = availableBodyHeight(options, area);
        double valueWidth = Math.max(160, area.width() - 175);
        int index = 0;
        if (records.isEmpty()) {
            pages.add(new Label("No entries in this database."));
            return pages;
        }
        while (index < records.size()) {
            VBox body = new VBox(12);
            double used = 0;
            while (index < records.size()) {
                RecordData record = records.get(index);
                double recordHeight = estimateRecordBlockHeight(fields, record, valueWidth);
                if (!body.getChildren().isEmpty() && used + recordHeight + 12 > bodyHeight) {
                    break;
                }
                body.getChildren().add(recordBlock(fields, record));
                used += Math.min(recordHeight + 12, bodyHeight);
                index++;
                if (used >= bodyHeight) {
                    break;
                }
            }
            pages.add(body);
        }
        return pages;
    }

    private static GridPane tableGrid(List<FieldDefinition> fields, double columnWidth) {
        GridPane grid = new GridPane();
        grid.setGridLinesVisible(true);
        int count = Math.max(1, fields.size());
        grid.setMinWidth(columnWidth * count);
        grid.setPrefWidth(columnWidth * count);
        grid.setMaxWidth(columnWidth * count);
        for (int i = 0; i < count; i++) {
            ColumnConstraints constraints = new ColumnConstraints(columnWidth, columnWidth, columnWidth);
            constraints.setHgrow(Priority.NEVER);
            grid.getColumnConstraints().add(constraints);
        }
        return grid;
    }

    private static void addHeadings(GridPane grid, List<FieldDefinition> fields, int row) {
        for (int col = 0; col < fields.size(); col++) {
            grid.add(cell(fields.get(col).name(), true), col, row);
        }
    }

    private static VBox recordBlock(List<FieldDefinition> fields, RecordData record) {
        Label recordTitle = new Label("Entry " + record.id());
        recordTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: black;");
        GridPane grid = new GridPane();
        grid.setGridLinesVisible(true);
        grid.getColumnConstraints().add(new ColumnConstraints(150));
        ColumnConstraints valueColumn = new ColumnConstraints();
        valueColumn.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().add(valueColumn);
        for (int row = 0; row < fields.size(); row++) {
            FieldDefinition field = fields.get(row);
            grid.add(cell(field.name(), true), 0, row);
            grid.add(cell(UiUtil.formatValue(field, record.value(field.id())), false), 1, row);
        }
        return new VBox(5, recordTitle, grid);
    }

    private static void appendSummaryBody(List<Node> bodies, List<FieldDefinition> fields,
                                          List<RecordData> records, PrintOptions options, PrintArea area) {
        List<Map.Entry<FieldDefinition, String>> entries = new ArrayList<>(summaries(fields, records).entrySet());
        if (entries.isEmpty()) {
            return;
        }
        double bodyHeight = availableBodyHeight(options, area);
        double lineWidth = Math.max(80, area.width() - 16);
        int index = 0;
        int summaryPage = 1;
        while (index < entries.size()) {
            VBox summary = new VBox(6);
            Label title = new Label(summaryPage == 1 ? "Field Summaries" : "Field Summaries (continued)");
            title.setFont(Font.font(15));
            title.setStyle("-fx-font-weight: bold; -fx-text-fill: black;");
            summary.getChildren().add(title);
            double used = 34;
            while (index < entries.size()) {
                Map.Entry<FieldDefinition, String> entry = entries.get(index);
                String text = entry.getKey().name() + " — "
                        + entry.getKey().summaryType().displayName() + ": " + entry.getValue();
                double lineHeight = estimateTextHeight(text, lineWidth) + 6;
                if (summary.getChildren().size() > 1 && used + lineHeight > bodyHeight) {
                    break;
                }
                Label line = new Label(text);
                line.setWrapText(true);
                line.setMaxWidth(lineWidth);
                line.setStyle("-fx-text-fill: black;");
                summary.getChildren().add(line);
                used += Math.min(lineHeight, bodyHeight);
                index++;
                if (used >= bodyHeight) {
                    break;
                }
            }
            bodies.add(summary);
            summaryPage++;
        }
    }

    static Map<FieldDefinition, String> summaries(List<FieldDefinition> fields, List<RecordData> records) {
        LinkedHashMap<FieldDefinition, String> result = new LinkedHashMap<>();
        NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.US);
        for (FieldDefinition field : fields) {
            SummaryType type = field.summaryType();
            if (type == SummaryType.NONE) {
                continue;
            }
            List<String> values = records.stream()
                    .map(record -> record.value(field.id()))
                    .filter(value -> value != null && !value.isBlank())
                    .toList();
            if (type == SummaryType.COUNT) {
                result.put(field, String.valueOf(values.size()));
                continue;
            }
            if (!field.type().isNumeric()) {
                result.put(field, "Not available");
                continue;
            }
            List<BigDecimal> numbers = values.stream().map(value -> {
                try {
                    return new BigDecimal(value);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }).filter(Objects::nonNull).toList();
            BigDecimal value = switch (type) {
                case SUM -> numbers.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                case AVERAGE -> numbers.isEmpty() ? BigDecimal.ZERO
                        : numbers.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(numbers.size()), 4, RoundingMode.HALF_UP);
                case MINIMUM -> numbers.stream().min(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
                case MAXIMUM -> numbers.stream().max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
                default -> BigDecimal.ZERO;
            };
            result.put(field, field.type() == FieldType.CURRENCY
                    ? currency.format(value) : value.stripTrailingZeros().toPlainString());
        }
        return result;
    }

    static double printableColumnWidth(PageLayout layout, int fieldCount) {
        return printableColumnWidth(new PrintArea(layout.getPrintableWidth(), layout.getPrintableHeight()), fieldCount);
    }

    private static double printableColumnWidth(PrintArea area, int fieldCount) {
        return Math.max(1, (area.width() - 8) / Math.max(1, fieldCount));
    }

    private static double availableBodyHeight(PrintOptions options, PrintArea area) {
        double reserved = PAGE_PADDING * 2;
        if (options.showDatabaseName() || options.showPrintDate()) {
            reserved += HEADER_RESERVE;
        }
        if (options.showPageNumbers()) {
            reserved += FOOTER_RESERVE;
        }
        return Math.max(100, area.height() - reserved);
    }

    private static double estimateHeadingHeight(List<FieldDefinition> fields, double width) {
        return fields.stream().mapToDouble(field -> estimateTextHeight(field.name(), width))
                .max().orElse(CELL_BASE_HEIGHT);
    }

    private static double estimateRecordRowHeight(List<FieldDefinition> fields, RecordData record, double width) {
        return fields.stream().mapToDouble(field ->
                        estimateTextHeight(UiUtil.formatValue(field, record.value(field.id())), width))
                .max().orElse(CELL_BASE_HEIGHT);
    }

    private static double estimateRecordBlockHeight(List<FieldDefinition> fields, RecordData record, double width) {
        double fieldsHeight = fields.stream().mapToDouble(field -> Math.max(
                estimateTextHeight(field.name(), 150),
                estimateTextHeight(UiUtil.formatValue(field, record.value(field.id())), width))).sum();
        return 28 + fieldsHeight;
    }

    private static double estimateTextHeight(String text, double width) {
        String safe = text == null ? "" : text;
        int charactersPerLine = Math.max(8, (int) (width / 7.0));
        int lines = 0;
        for (String explicitLine : safe.split("\\R", -1)) {
            lines += Math.max(1, (explicitLine.length() + charactersPerLine - 1) / charactersPerLine);
        }
        return Math.max(CELL_BASE_HEIGHT, 10 + lines * TEXT_LINE_HEIGHT);
    }

    private static BorderPane page(DatabaseInfo info, Node body, PrintOptions options,
                                   int pageNumber, int pageCount, PrintArea area) {
        BorderPane page = new BorderPane();
        page.setPrefSize(area.width(), area.height());
        page.setMinSize(area.width(), area.height());
        page.setMaxSize(area.width(), area.height());
        page.setPadding(new Insets(4));
        page.setStyle("-fx-background-color: white; -fx-text-fill: black;");

        if (options.showDatabaseName() || options.showPrintDate()) {
            HBox header = new HBox(12);
            header.setAlignment(Pos.CENTER_LEFT);
            if (options.showDatabaseName()) {
                Label title = new Label(info.name());
                title.setFont(Font.font(16));
                title.setStyle("-fx-font-weight: bold; -fx-text-fill: black;");
                header.getChildren().add(title);
            }
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            header.getChildren().add(spacer);
            if (options.showPrintDate()) {
                Label date = new Label("Printed " + PRINT_DATE.format(LocalDateTime.now()));
                date.setStyle("-fx-text-fill: black;");
                header.getChildren().add(date);
            }
            page.setTop(header);
            BorderPane.setMargin(header, new Insets(0, 0, 8, 0));
        }
        page.setCenter(body);
        if (options.showPageNumbers()) {
            Label footer = new Label("Page " + pageNumber + " of " + pageCount);
            footer.setMaxWidth(Double.MAX_VALUE);
            footer.setAlignment(Pos.CENTER);
            footer.setStyle("-fx-text-fill: black;");
            page.setBottom(footer);
            BorderPane.setMargin(footer, new Insets(8, 0, 0, 0));
        }
        return page;
    }

    private static Label cell(String text, boolean heading) {
        Label label = new Label(text == null ? "" : text);
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setMinHeight(24);
        label.setPadding(new Insets(4, 6, 4, 6));
        label.setStyle("-fx-text-fill: black;" + (heading
                ? "-fx-font-weight: bold; -fx-background-color: #e9eef3;"
                : "-fx-background-color: white;"));
        return label;
    }

    private record PrintArea(double width, double height) {
    }
}
