package org.pindb.ui;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.pindb.AppContext;
import org.pindb.db.DatabaseException;
import org.pindb.db.DatabaseService;
import org.pindb.db.DocumentStore;
import org.pindb.model.DatabaseInfo;
import org.pindb.model.DatabaseView;
import org.pindb.model.DocumentData;
import org.pindb.model.FieldDefinition;
import org.pindb.model.FieldType;
import org.pindb.model.FilterSpec;
import org.pindb.model.RecordData;
import org.pindb.service.CsvService;
import org.pindb.service.PrintService;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class DatabaseWindow {
    private static final DateTimeFormatter MODIFIED_FORMAT = DateTimeFormatter.ofPattern("MMM d, uuuu h:mm a");

    private final AppContext context;
    private final DatabaseService database;
    private final DocumentStore documentStore;
    private final Runnable onClosed;
    private final Stage stage = new Stage();
    private final ObservableList<RecordData> records = FXCollections.observableArrayList();
    private final FilteredList<RecordData> filtered = new FilteredList<>(records, record -> true);
    private final TableView<RecordData> table = new TableView<>();
    private final ListView<RecordData> recordList = new ListView<>();
    private final StackPane viewStack = new StackPane();
    private final FlowPane summaries = new FlowPane(12, 12);
    private final TextField search = new TextField();
    private final Label nameLabel = UiUtil.title("");
    private final Label detailsLabel = new Label();
    private final Label statusLabel = new Label();
    private final Button editButton = new Button("Edit");
    private final Button addButton = UiUtil.primaryButton("+");
    private final Button deleteButton = new Button("−");
    private final Scene scene;
    private List<FieldDefinition> fields = List.of();
    private Map<Long, FieldDefinition> fieldsById = Map.of();
    private FilterSpec filterSpec = FilterSpec.empty();
    private DatabaseView view;
    private boolean closed;

    public DatabaseWindow(AppContext context, DatabaseService database, Runnable onClosed) {
        this.context = context;
        this.database = database;
        this.documentStore = new DocumentStore(database.path());
        this.onClosed = onClosed;
        DatabaseInfo info = database.info();
        view = info.defaultView();

        stage.setTitle(info.name() + " — PinDB");
        stage.getIcons().add(new Image(Objects.requireNonNull(
                getClass().getResourceAsStream("/org/pindb/app/icon-64.png"))));

        BorderPane root = new BorderPane();
        root.setTop(new VBox(menuBar(), header()));
        root.setCenter(content());
        root.setBottom(bottomBar());
        scene = new Scene(root, 1180, 760);
        UiUtil.applyStyles(scene, context.settings());
        stage.setScene(scene);
        stage.setMinWidth(840);
        stage.setMinHeight(570);
        stage.setOnCloseRequest(event -> close());

        search.setPromptText("Search all fields…");
        search.textProperty().addListener((observable, oldValue, newValue) -> updatePredicate());
        table.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && selectedRecord() != null) {
                editSelected();
            }
        });
        recordList.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && selectedRecord() != null) {
                editSelected();
            }
        });
        table.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> updateActionState());
        recordList.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> updateActionState());
        recordList.setCellFactory(list -> new RecordCell());

        addButton.getStyleClass().add("icon-button");
        deleteButton.getStyleClass().addAll("icon-button", "danger");
        addButton.setTooltip(new javafx.scene.control.Tooltip("Add entry"));
        deleteButton.setTooltip(new javafx.scene.control.Tooltip("Move selected entry to Recently Deleted"));
        addButton.setOnAction(event -> addEntry());
        editButton.setOnAction(event -> editSelected());
        deleteButton.setOnAction(event -> deleteSelected());

        reloadAll();
        showView(view, false);
    }

    public void showAndFocus() {
        stage.show();
        stage.toFront();
        stage.requestFocus();
    }

    public void refreshStyle() {
        UiUtil.applyStyles(scene, context.settings());
    }

    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        documentStore.close();
        database.close();
        stage.hide();
        onClosed.run();
    }

    private MenuBar menuBar() {
        Menu file = new Menu("File");
        MenuItem add = item("New Entry", event -> addEntry());
        add.setAccelerator(new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN));
        MenuItem export = item("Export Visible Entries to CSV…", event -> exportCsv());
        MenuItem print = item("Print…", event -> printDatabase());
        print.setAccelerator(new KeyCodeCombination(KeyCode.P, KeyCombination.CONTROL_DOWN));
        MenuItem close = item("Close Database", event -> close());
        close.setAccelerator(new KeyCodeCombination(KeyCode.W, KeyCombination.CONTROL_DOWN));
        file.getItems().addAll(add, new javafx.scene.control.SeparatorMenuItem(), export, print,
                new javafx.scene.control.SeparatorMenuItem(), close);

        Menu databaseMenu = new Menu("Database");
        databaseMenu.getItems().addAll(
                item("Manage Fields…", event -> new ManageFieldsDialog(stage, context.settings(), database, this::reloadAll).showAndWait()),
                item("Backups…", event -> new BackupRestoreDialog(stage, context.settings(), database, this::reloadAll).showAndWait()),
                item("Recently Deleted…", event -> new TrashDialog(stage, context.settings(), database, this::reloadAll).showAndWait()),
                new javafx.scene.control.SeparatorMenuItem(),
                item("Check Database Integrity", event -> checkIntegrity()));

        Menu viewMenu = new Menu("View");
        ToggleGroup viewGroup = new ToggleGroup();
        RadioMenuItem tableItem = new RadioMenuItem("Table View");
        RadioMenuItem recordItem = new RadioMenuItem("Record View");
        tableItem.setToggleGroup(viewGroup);
        recordItem.setToggleGroup(viewGroup);
        tableItem.setSelected(view == DatabaseView.TABLE);
        recordItem.setSelected(view == DatabaseView.RECORD);
        tableItem.setOnAction(event -> showView(DatabaseView.TABLE, true));
        recordItem.setOnAction(event -> showView(DatabaseView.RECORD, true));
        MenuItem clearFilter = item("Clear Search and Filters", event -> {
            search.clear();
            filterSpec = FilterSpec.empty();
            updatePredicate();
        });
        viewMenu.getItems().addAll(tableItem, recordItem, new javafx.scene.control.SeparatorMenuItem(), clearFilter);

        Menu help = new Menu("Help");
        help.getItems().addAll(
                item("PinDB Help", event -> new HelpDialog(stage, context.settings()).showAndWait()),
                item("Check for Updates", event -> context.checkForUpdates(stage, true)),
                new javafx.scene.control.SeparatorMenuItem(),
                item("Report a Bug…", event -> new BugReportDialog(stage, context.settings()).showAndWait()),
                item("About PinDB", event -> new AboutDialog(stage, context.settings()).showAndWait()));
        return new MenuBar(file, databaseMenu, viewMenu, help);
    }

    private javafx.scene.Node header() {
        DatabaseInfo info = database.info();
        nameLabel.setText(info.name());
        detailsLabel.getStyleClass().add("subtitle-label");
        detailsLabel.setWrapText(true);
        statusLabel.getStyleClass().add("muted-label");

        VBox titles = new VBox(3, nameLabel, detailsLabel);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button filter = new Button("Filter…");
        filter.setOnAction(event -> new FilterDialog(stage, context.settings(), fields, filterSpec)
                .showAndWait().ifPresent(result -> {
                    filterSpec = result;
                    updatePredicate();
                }));
        Button fieldsButton = new Button("Fields…");
        fieldsButton.setOnAction(event -> new ManageFieldsDialog(stage, context.settings(), database, this::reloadAll).showAndWait());
        search.setPrefWidth(260);
        HBox tools = new HBox(8, search, filter, fieldsButton);
        tools.setAlignment(Pos.CENTER_RIGHT);
        HBox top = new HBox(14, titles, spacer, tools);
        top.setAlignment(Pos.CENTER_LEFT);

        VBox header = new VBox(12, top, summaries);
        header.setPadding(new Insets(16, 18, 12, 18));
        return header;
    }

    private javafx.scene.Node content() {
        table.setPlaceholder(new Label("No entries yet. Select + to create the first entry."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        recordList.setPlaceholder(new Label("No entries yet. Select + to create the first entry."));
        viewStack.getChildren().addAll(table, recordList);
        VBox.setVgrow(viewStack, Priority.ALWAYS);
        VBox box = new VBox(viewStack);
        box.setPadding(new Insets(0, 18, 12, 18));
        return box;
    }

    private javafx.scene.Node bottomBar() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(8, editButton, deleteButton, addButton);
        actions.setAlignment(Pos.CENTER_RIGHT);
        HBox bar = new HBox(12, statusLabel, spacer, actions);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10, 18, 14, 18));
        return bar;
    }

    private void reloadAll() {
        try {
            fields = database.fields();
            LinkedHashMap<Long, FieldDefinition> map = new LinkedHashMap<>();
            fields.forEach(field -> map.put(field.id(), field));
            fieldsById = Map.copyOf(map);
            records.setAll(database.activeRecords());
            rebuildTableColumns();
            refreshSummaryCards();
            DatabaseInfo info = database.info();
            stage.setTitle(info.name() + " — PinDB");
            nameLabel.setText(info.name());
            detailsLabel.setText(records.size() + (records.size() == 1 ? " entry" : " entries")
                    + "  •  " + database.path());
            updatePredicate();
            updateActionState();
            statusLabel.setText("Saved automatically");
        } catch (RuntimeException exception) {
            UiUtil.error(stage, "Database Error", "PinDB could not refresh this database.", exception);
        }
    }

    private void rebuildTableColumns() {
        table.getColumns().clear();
        for (FieldDefinition field : fields) {
            TableColumn<RecordData, String> column = new TableColumn<>(field.name());
            column.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().value(field.id())));
            column.setCellFactory(ignored -> new javafx.scene.control.TableCell<>() {
                @Override
                protected void updateItem(String value, boolean empty) {
                    super.updateItem(value, empty);
                    setGraphic(null);
                    setText(null);
                    if (empty) {
                        return;
                    }
                    if (field.type() == FieldType.DOCUMENT && value != null && !value.isBlank()) {
                        Hyperlink link = new Hyperlink(value);
                        link.setOnAction(event -> {
                            RecordData record = getTableRow() == null ? null : getTableRow().getItem();
                            if (record != null) {
                                openDocument(record.id(), field.id());
                            }
                        });
                        setGraphic(link);
                    } else {
                        setText(UiUtil.formatValue(field, value));
                        setWrapText(field.type() == FieldType.MULTILINE_TEXT);
                    }
                }
            });
            column.setComparator(comparatorFor(field));
            column.setMinWidth(field.type() == FieldType.MULTILINE_TEXT ? 180 : 110);
            table.getColumns().add(column);
        }
        SortedList<RecordData> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);
        recordList.setItems(filtered);
    }

    private Comparator<String> comparatorFor(FieldDefinition field) {
        return switch (field.type()) {
            case NUMBER, CURRENCY -> Comparator.comparing(value -> {
                try {
                    return value == null || value.isBlank() ? null : new BigDecimal(value);
                } catch (RuntimeException exception) {
                    return null;
                }
            }, Comparator.nullsFirst(Comparator.naturalOrder()));
            case DATE, DATE_TIME -> Comparator.nullsFirst(String::compareTo);
            case BOOLEAN -> Comparator.comparing(Boolean::parseBoolean);
            default -> Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER);
        };
    }

    private void refreshSummaryCards() {
        summaries.getChildren().clear();
        Map<FieldDefinition, String> values = database.summaries();
        for (Map.Entry<FieldDefinition, String> entry : values.entrySet()) {
            Label field = new Label(entry.getKey().name() + " — " + entry.getKey().summaryType().displayName());
            field.getStyleClass().add("summary-name");
            Label value = new Label(entry.getValue());
            value.getStyleClass().add("summary-value");
            VBox card = new VBox(3, field, value);
            card.getStyleClass().add("summary-card");
            summaries.getChildren().add(card);
        }
        if (values.isEmpty()) {
            Label noSummaries = new Label("No field summaries configured. Use Fields… to add one.");
            noSummaries.getStyleClass().add("muted-label");
            summaries.getChildren().add(noSummaries);
        }
    }

    private void updatePredicate() {
        String query = search.getText() == null ? "" : search.getText().trim().toLowerCase();
        filtered.setPredicate(record -> {
            if (!filterSpec.matches(record, fieldsById)) {
                return false;
            }
            if (query.isBlank()) {
                return true;
            }
            if (String.valueOf(record.id()).contains(query)) {
                return true;
            }
            for (FieldDefinition field : fields) {
                String raw = record.value(field.id());
                if (raw.toLowerCase().contains(query)
                        || UiUtil.formatValue(field, raw).toLowerCase().contains(query)) {
                    return true;
                }
            }
            return false;
        });
        statusLabel.setText(filtered.size() + " of " + records.size() + " entries shown");
    }

    private void showView(DatabaseView next, boolean save) {
        view = next;
        table.setVisible(next == DatabaseView.TABLE);
        table.setManaged(next == DatabaseView.TABLE);
        recordList.setVisible(next == DatabaseView.RECORD);
        recordList.setManaged(next == DatabaseView.RECORD);
        if (save) {
            database.setMeta("default_view", next.name());
        }
        updateActionState();
    }

    private RecordData selectedRecord() {
        return view == DatabaseView.TABLE
                ? table.getSelectionModel().getSelectedItem()
                : recordList.getSelectionModel().getSelectedItem();
    }

    private void updateActionState() {
        boolean hasSelection = selectedRecord() != null;
        editButton.setVisible(hasSelection);
        editButton.setManaged(hasSelection);
        deleteButton.setDisable(!hasSelection);
    }

    private void addEntry() {
        boolean addAnother;
        do {
            EntryEditorDialog.Result result = new EntryEditorDialog(
                    stage, context.settings(), fields, null, Map.of()).showAndWait().orElse(null);
            if (result == null) {
                return;
            }
            try {
                long recordId = database.addRecord(result.values());
                documentStore.replaceDocuments(recordId, result.documents());
                reloadAll();
                addAnother = result.addAnother();
            } catch (DatabaseException exception) {
                UiUtil.warning(stage, "Entry Not Saved", exception.getMessage());
                addAnother = false;
            }
        } while (addAnother);
    }

    private void editSelected() {
        RecordData selected = selectedRecord();
        if (selected == null) {
            return;
        }
        Map<Long, DocumentData> existingDocuments = documentStore.documentsForRecord(selected.id());
        new EntryEditorDialog(stage, context.settings(), fields, selected, existingDocuments)
                .showAndWait().ifPresent(result -> {
            try {
                database.updateRecord(selected.id(), result.values());
                documentStore.replaceDocuments(selected.id(), result.documents());
                reloadAll();
            } catch (DatabaseException exception) {
                UiUtil.warning(stage, "Entry Not Saved", exception.getMessage());
            }
        });
    }

    private void deleteSelected() {
        RecordData selected = selectedRecord();
        if (selected == null) {
            return;
        }
        DatabaseInfo info = database.info();
        boolean approved = info.suppressDeleteConfirmation() || confirmDelete(selected);
        if (!approved) {
            return;
        }
        try {
            database.moveToTrash(selected.id());
            reloadAll();
        } catch (RuntimeException exception) {
            UiUtil.error(stage, "Could Not Delete Entry", "The selected entry could not be moved to Recently Deleted.", exception);
        }
    }

    private boolean confirmDelete(RecordData selected) {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.initOwner(stage);
        dialog.setTitle("Delete Entry");
        dialog.setHeaderText("Move entry " + selected.id() + " to Recently Deleted?");
        ButtonType delete = new ButtonType("Move to Recently Deleted", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(delete, ButtonType.CANCEL);
        CheckBox doNotShow = new CheckBox("Do not show this confirmation again for this database");
        Label explanation = new Label("The entry can be restored later from Database → Recently Deleted.");
        explanation.setWrapText(true);
        dialog.getDialogPane().setContent(new VBox(10, explanation, doNotShow));
        dialog.setResultConverter(button -> {
            if (button == delete && doNotShow.isSelected()) {
                database.setMeta("suppress_delete_confirmation", "true");
            }
            return button == delete;
        });
        dialog.getDialogPane().sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene != null) {
                UiUtil.applyStyles(newScene, context.settings());
            }
        });
        return dialog.showAndWait().orElse(false);
    }

    private void openDocument(long recordId, long fieldId) {
        documentStore.document(recordId, fieldId).ifPresentOrElse(
                document -> new DocumentViewer(stage, context.settings(), document).show(),
                () -> UiUtil.warning(stage, "Document Unavailable",
                        "The selected document is no longer stored in this entry."));
    }

    private void exportCsv() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export PinDB Database to CSV");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        String base = database.info().name().replaceAll("[^A-Za-z0-9._-]+", "-");
        chooser.setInitialFileName((base.isBlank() ? "pindb-export" : base) + ".csv");
        File file = chooser.showSaveDialog(stage);
        if (file == null) {
            return;
        }
        Path destination = file.toPath().getFileName().toString().toLowerCase().endsWith(".csv")
                ? file.toPath() : file.toPath().resolveSibling(file.getName() + ".csv");
        try {
            CsvService.exportCsv(destination, fields, new ArrayList<>(filtered));
            UiUtil.information(stage, "CSV Export Complete", "The visible entries were exported to:\n" + destination);
        } catch (RuntimeException exception) {
            UiUtil.error(stage, "CSV Export Failed", "PinDB could not export this database.", exception);
        }
    }

    private void printDatabase() {
        new PrintOptionsDialog(stage, context.settings(), database, fields).showAndWait().ifPresent(options -> {
            try {
                boolean printed = PrintService.print(stage, database.info(), fields,
                        new ArrayList<>(filtered), options);
                if (!printed) {
                    statusLabel.setText("Printing cancelled or unsuccessful");
                }
            } catch (RuntimeException exception) {
                UiUtil.error(stage, "Printing Failed", "PinDB could not print this database.", exception);
            }
        });
    }

    private void checkIntegrity() {
        try {
            if (database.integrityCheck()) {
                UiUtil.information(stage, "Database Integrity", "SQLite reports that this database is healthy.");
            } else {
                UiUtil.warning(stage, "Database Integrity Warning",
                        "SQLite found a problem in this database. Restore a recent backup before making further changes.");
            }
        } catch (RuntimeException exception) {
            UiUtil.error(stage, "Integrity Check Failed", "PinDB could not check this database.", exception);
        }
    }

    private MenuItem item(String text, javafx.event.EventHandler<javafx.event.ActionEvent> action) {
        MenuItem item = new MenuItem(text);
        item.setOnAction(action);
        return item;
    }

    private final class RecordCell extends ListCell<RecordData> {
        @Override
        protected void updateItem(RecordData record, boolean empty) {
            super.updateItem(record, empty);
            if (empty || record == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            GridPane grid = new GridPane();
            grid.setHgap(14);
            grid.setVgap(7);
            int row = 0;
            for (FieldDefinition field : fields) {
                Label name = new Label(field.name() + ":");
                name.setStyle("-fx-font-weight: bold;");
                javafx.scene.Node value;
                if (field.type() == FieldType.DOCUMENT && !record.value(field.id()).isBlank()) {
                    Hyperlink link = new Hyperlink(record.value(field.id()));
                    link.setOnAction(event -> openDocument(record.id(), field.id()));
                    value = link;
                } else {
                    Label label = new Label(UiUtil.formatValue(field, record.value(field.id())));
                    label.setWrapText(true);
                    label.setMaxWidth(Double.MAX_VALUE);
                    value = label;
                }
                grid.add(name, 0, row);
                grid.add(value, 1, row);
                GridPane.setHgrow(value, Priority.ALWAYS);
                row++;
            }
            Label metadata = new Label("Entry " + record.id() + "  •  Updated "
                    + MODIFIED_FORMAT.format(record.updatedAt()));
            metadata.getStyleClass().add("muted-label");
            VBox card = new VBox(10, grid, new Separator(), metadata);
            card.getStyleClass().add("record-card");
            card.setMaxWidth(Double.MAX_VALUE);
            setGraphic(card);
            setText(null);
            setPadding(new Insets(6, 4, 6, 4));
        }
    }
}
