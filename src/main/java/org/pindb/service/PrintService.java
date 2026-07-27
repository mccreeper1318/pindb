package org.pindb.service;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.print.PageLayout;
import javafx.print.PageOrientation;
import javafx.print.Paper;
import javafx.print.Printer;
import javafx.print.PrinterJob;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Window;
import org.pindb.model.DatabaseInfo;
import org.pindb.model.FieldDefinition;
import org.pindb.model.PrintArrangement;
import org.pindb.model.PrintOptions;
import org.pindb.model.RecordData;
import org.pindb.ui.UiUtil;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PrintService {
    private static final DateTimeFormatter PRINT_DATE = DateTimeFormatter.ofPattern("MMM d, uuuu h:mm a");

    private PrintService() {
    }

    public static boolean print(Window owner, DatabaseInfo info, List<FieldDefinition> allFields,
                                List<RecordData> records, PrintOptions options) {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) {
            UiUtil.warning(owner, "Printing Unavailable", "No printer service is available on this system.");
            return false;
        }
        PageOrientation orientation = options.landscape() ? PageOrientation.LANDSCAPE : PageOrientation.PORTRAIT;
        Printer printer = job.getPrinter();
        PageLayout pageLayout = printer.createPageLayout(Paper.A4, orientation, Printer.MarginType.DEFAULT);
        job.getJobSettings().setPageLayout(pageLayout);
        if (!job.showPrintDialog(owner)) {
            return false;
        }

        List<FieldDefinition> fields = selectedFields(allFields, options.fieldIds());
        List<Node> pages = options.arrangement() == PrintArrangement.COLUMNS
                ? columnPages(info, fields, records, options, pageLayout)
                : rowPages(info, fields, records, options, pageLayout);
        boolean success = true;
        for (Node page : pages) {
            page.applyCss();
            page.autosize();
            if (!job.printPage(pageLayout, page)) {
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
        return selectedIds.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
    }

    private static List<Node> columnPages(DatabaseInfo info, List<FieldDefinition> fields,
                                          List<RecordData> records, PrintOptions options, PageLayout layout) {
        int rowsPerPage = options.landscape() ? 25 : 34;
        int pageCount = Math.max(1, (records.size() + rowsPerPage - 1) / rowsPerPage);
        List<Node> pages = new ArrayList<>();
        for (int page = 0; page < pageCount; page++) {
            int from = page * rowsPerPage;
            int to = Math.min(records.size(), from + rowsPerPage);
            GridPane grid = new GridPane();
            grid.setGridLinesVisible(true);
            grid.setMaxWidth(Double.MAX_VALUE);
            int row = 0;
            if (options.repeatHeadings() || page == 0) {
                for (int col = 0; col < fields.size(); col++) {
                    Label heading = cell(fields.get(col).name(), true);
                    grid.add(heading, col, row);
                }
                row++;
            }
            for (int index = from; index < to; index++) {
                RecordData record = records.get(index);
                for (int col = 0; col < fields.size(); col++) {
                    FieldDefinition field = fields.get(col);
                    grid.add(cell(UiUtil.formatValue(field, record.value(field.id())), false), col, row);
                }
                row++;
            }
            double percent = fields.isEmpty() ? 100 : 100.0 / fields.size();
            for (int i = 0; i < fields.size(); i++) {
                ColumnConstraints constraints = new ColumnConstraints();
                constraints.setPercentWidth(percent);
                constraints.setHgrow(Priority.ALWAYS);
                grid.getColumnConstraints().add(constraints);
            }
            pages.add(page(info, grid, options, page + 1, pageCount, layout));
        }
        return pages;
    }

    private static List<Node> rowPages(DatabaseInfo info, List<FieldDefinition> fields,
                                       List<RecordData> records, PrintOptions options, PageLayout layout) {
        int recordsPerPage = options.landscape() ? 5 : 4;
        int pageCount = Math.max(1, (records.size() + recordsPerPage - 1) / recordsPerPage);
        List<Node> pages = new ArrayList<>();
        for (int page = 0; page < pageCount; page++) {
            VBox body = new VBox(12);
            int from = page * recordsPerPage;
            int to = Math.min(records.size(), from + recordsPerPage);
            if (records.isEmpty()) {
                body.getChildren().add(new Label("No entries in this database."));
            }
            for (int index = from; index < to; index++) {
                RecordData record = records.get(index);
                GridPane grid = new GridPane();
                grid.setGridLinesVisible(true);
                grid.getColumnConstraints().add(new ColumnConstraints(150));
                ColumnConstraints valueColumn = new ColumnConstraints();
                valueColumn.setHgrow(Priority.ALWAYS);
                grid.getColumnConstraints().add(valueColumn);
                int row = 0;
                Label recordTitle = new Label("Entry " + record.id());
                recordTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
                body.getChildren().add(recordTitle);
                for (FieldDefinition field : fields) {
                    grid.add(cell(field.name(), true), 0, row);
                    grid.add(cell(UiUtil.formatValue(field, record.value(field.id())), false), 1, row);
                    row++;
                }
                body.getChildren().add(grid);
            }
            pages.add(page(info, body, options, page + 1, pageCount, layout));
        }
        return pages;
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
            javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
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
