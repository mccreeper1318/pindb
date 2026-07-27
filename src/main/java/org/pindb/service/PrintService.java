package org.pindb.service;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.print.PageLayout;
import javafx.print.PageOrientation;
import javafx.print.Printer;
import javafx.print.PrinterJob;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Window;
import org.pindb.model.DatabaseInfo;
import org.pindb.model.FieldDefinition;
import org.pindb.model.FieldType;
import org.pindb.model.PrintArrangement;
import org.pindb.model.PrintOptions;
import org.pindb.model.RecordData;
import org.pindb.model.SummaryType;
import org.pindb.ui.UiUtil;

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

public final class PrintService {
    private static final DateTimeFormatter PRINT_DATE = DateTimeFormatter.ofPattern("MMM d, uuuu h:mm a");
    private static final double HEADER_RESERVE = 38;
    private static final double FOOTER_RESERVE = 30;
    private static final double PAGE_PADDING = 12;
    private static final double CELL_BASE_HEIGHT = 25;
    private static final double TEXT_LINE_HEIGHT = 15;

    private PrintService() {
    }

    public static boolean print(Window owner, DatabaseInfo info, List<FieldDefinition> allFields,
                                List<RecordData> records, PrintOptions options) {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) {
            UiUtil.warning(owner, "Printing Unavailable",
                    "PinDB could not find a configured printer. Add a printer in Linux system settings, "
                            + "then restart PinDB.");
            return false;
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

        List<FieldDefinition> fields = selectedFields(allFields, options.fieldIds());
        List<Node> bodies = options.arrangement() == PrintArrangement.COLUMNS
                ? columnBodies(fields, records, options, pageLayout)
                : rowBodies(fields, records, options, pageLayout);
        if (options.includeSummaries()) {
            appendSummaryBody(bodies, fields, records, options, pageLayout);
        }
        if (bodies.isEmpty()) {
            bodies.add(new Label("No entries in this database."));
        }

        boolean success = true;
        int pageCount = bodies.size();
        for (int index = 0; index < pageCount; index++) {
            Node printablePage = page(info, bodies.get(index), options, index + 1, pageCount, pageLayout);
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

    private static List<FieldDefinition> selectedFields(List<FieldDefinition> all, List<Long> selectedIds) {
        Map<Long, FieldDefinition> byId = new LinkedHashMap<>();
        all.forEach(field -> byId.put(field.id(), field));
        return selectedIds.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    private static List<Node> columnBodies(List<FieldDefinition> fields, List<RecordData> records,
                                           PrintOptions options, PageLayout layout) {
        List<Node> pages = new ArrayList<>();
        double bodyHeight = availableBodyHeight(options, layout);
        double columnWidth = Math.max(70, layout.getPrintableWidth() / Math.max(1, fields.size()));
        int index = 0;
        boolean firstPage = true;
        while (index < records.size() || (records.isEmpty() && firstPage)) {
            GridPane grid = tableGrid(fields);
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
                                        PrintOptions options, PageLayout layout) {
        List<Node> pages = new ArrayList<>();
        double bodyHeight = availableBodyHeight(options, layout);
        double valueWidth = Math.max(160, layout.getPrintableWidth() - 175);
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

    private static GridPane tableGrid(List<FieldDefinition> fields) {
        GridPane grid = new GridPane();
        grid.setGridLinesVisible(true);
        grid.setMaxWidth(Double.MAX_VALUE);
        double percent = fields.isEmpty() ? 100 : 100.0 / fields.size();
        for (int i = 0; i < Math.max(1, fields.size()); i++) {
            ColumnConstraints constraints = new ColumnConstraints();
            constraints.setPercentWidth(percent);
            constraints.setHgrow(Priority.ALWAYS);
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
                                          List<RecordData> records, PrintOptions options, PageLayout layout) {
        Map<FieldDefinition, String> summaries = summaries(fields, records);
        if (summaries.isEmpty()) {
            return;
        }
        VBox summary = new VBox(6);
        Label title = new Label("Field Summaries");
        title.setFont(Font.font(15));
        title.setStyle("-fx-font-weight: bold; -fx-text-fill: black;");
        summary.getChildren().add(title);
        for (Map.Entry<FieldDefinition, String> entry : summaries.entrySet()) {
            Label line = new Label(entry.getKey().name() + " — "
                    + entry.getKey().summaryType().displayName() + ": " + entry.getValue());
            line.setWrapText(true);
            line.setStyle("-fx-text-fill: black;");
            summary.getChildren().add(line);
        }
        double estimated = 34 + summaries.size() * 24.0;
        if (estimated > availableBodyHeight(options, layout)) {
            summary.setScaleX(0.9);
            summary.setScaleY(0.9);
        }
        bodies.add(summary);
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

    private static double availableBodyHeight(PrintOptions options, PageLayout layout) {
        double reserved = PAGE_PADDING * 2;
        if (options.showDatabaseName() || options.showPrintDate()) {
            reserved += HEADER_RESERVE;
        }
        if (options.showPageNumbers()) {
            reserved += FOOTER_RESERVE;
        }
        return Math.max(100, layout.getPrintableHeight() - reserved);
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
                                   int pageNumber, int pageCount, PageLayout layout) {
        BorderPane page = new BorderPane();
        page.setPrefSize(layout.getPrintableWidth(), layout.getPrintableHeight());
        page.setMinSize(layout.getPrintableWidth(), layout.getPrintableHeight());
        page.setMaxSize(layout.getPrintableWidth(), layout.getPrintableHeight());
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
}
