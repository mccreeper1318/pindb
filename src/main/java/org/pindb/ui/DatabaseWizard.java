package org.pindb.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.pindb.db.DatabaseService;
import org.pindb.model.DatabaseView;
import org.pindb.model.FieldDefinition;
import org.pindb.service.SettingsService;
import org.pindb.service.TemplateService;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class DatabaseWizard {
    private final Stage stage = new Stage();
    private final SettingsService settings;
    private final ObservableList<FieldDefinition> fields = FXCollections.observableArrayList();
    private final ListView<FieldDefinition> fieldList = new ListView<>(fields);
    private final TextField databaseName = new TextField("Untitled Database");
    private final TextArea description = new TextArea();
    private final TextField destination = new TextField();
    private final ComboBox<TemplateService.Template> template = new ComboBox<>();
    private final ComboBox<DatabaseView> defaultView = new ComboBox<>();
    private final Spinner<Integer> backupLimit = new Spinner<>(1, 100, 10);
    private final Label review = new Label();
    private final Label error = new Label();
    private final StackPane pages = new StackPane();
    private final List<javafx.scene.Node> pageNodes = new ArrayList<>();
    private final ListView<String> stepList = new ListView<>();
    private final Button back = new Button("Back");
    private final Button next = UiUtil.primaryButton("Next");
    private final Button cancel = new Button("Cancel");
    private final AtomicReference<Path> result = new AtomicReference<>();
    private int page;

    private DatabaseWizard(Window owner, SettingsService settings, TemplateService.Template initialTemplate) {
        this.settings = settings;
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("Create New PinDB Database");
        stage.getIcons().add(new javafx.scene.image.Image(
                java.util.Objects.requireNonNull(getClass().getResourceAsStream("/org/pindb/app/icon-64.png"))));

        stepList.setItems(FXCollections.observableArrayList(
                "1. Database Details", "2. Fields", "3. Preferences", "4. Review"));
        stepList.setMouseTransparent(true);
        stepList.setFocusTraversable(false);
        stepList.setPrefWidth(190);
        stepList.getStyleClass().add("step-list");

        template.setItems(FXCollections.observableArrayList(TemplateService.Template.values()));
        template.setValue(initialTemplate == null ? TemplateService.Template.BLANK : initialTemplate);
        defaultView.setItems(FXCollections.observableArrayList(DatabaseView.values()));
        defaultView.setValue(DatabaseView.TABLE);
        description.setPrefRowCount(5);
        description.setWrapText(true);
        destination.setPromptText("Choose where the .pindb file will be saved");
        backupLimit.setEditable(true);
        error.getStyleClass().add("error-label");
        error.setWrapText(true);

        pageNodes.add(detailsPage());
        pageNodes.add(fieldsPage());
        pageNodes.add(preferencesPage());
        pageNodes.add(reviewPage());
        pages.getChildren().addAll(pageNodes);

        fields.setAll(TemplateService.fieldsFor(template.getValue()));
        template.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                fields.setAll(TemplateService.fieldsFor(newValue));
                normalizeFieldPositions();
            }
        });

        HBox buttons = new HBox(10, error, new Separator(javafx.geometry.Orientation.VERTICAL), cancel, back, next);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.setPadding(new Insets(12));
        HBox.setHgrow(error, Priority.ALWAYS);

        BorderPane root = new BorderPane();
        root.setLeft(stepList);
        root.setCenter(pages);
        root.setBottom(buttons);
        BorderPane.setMargin(stepList, new Insets(12, 0, 12, 12));
        BorderPane.setMargin(pages, new Insets(12));

        Scene scene = new Scene(root, 900, 660);
        UiUtil.applyStyles(scene, settings);
        stage.setScene(scene);
        stage.setMinWidth(760);
        stage.setMinHeight(580);

        back.setOnAction(event -> showPage(page - 1));
        next.setOnAction(event -> advance());
        cancel.setOnAction(event -> stage.close());
        databaseName.textProperty().addListener((observable, oldValue, newValue) -> suggestDestination());
        suggestDestination();
        showPage(0);
    }

    public static Optional<Path> show(Window owner, SettingsService settings, TemplateService.Template initialTemplate) {
        DatabaseWizard wizard = new DatabaseWizard(owner, settings, initialTemplate);
        wizard.stage.showAndWait();
        return Optional.ofNullable(wizard.result.get());
    }

    private javafx.scene.Node detailsPage() {
        VBox content = new VBox(14);
        content.getStyleClass().add("panel-card");
        content.getChildren().addAll(UiUtil.title("Database Details"),
                subtitle("Name the database, choose a template, and select its file location."));

        GridPane grid = formGrid();
        int row = 0;
        addFormRow(grid, "Database name", databaseName, row++);
        addFormRow(grid, "Description", description, row++);
        addFormRow(grid, "Template", template, row++);

        Button browse = new Button("Browse…");
        browse.setOnAction(event -> chooseDestination());
        HBox pathRow = new HBox(8, destination, browse);
        HBox.setHgrow(destination, Priority.ALWAYS);
        addFormRow(grid, "Save as", pathRow, row);
        content.getChildren().add(grid);
        return content;
    }

    private javafx.scene.Node fieldsPage() {
        VBox content = new VBox(12);
        content.getStyleClass().add("panel-card");
        content.getChildren().addAll(UiUtil.title("Database Fields"),
                subtitle("Add, edit, remove, or drag fields into the order they should appear."));

        fieldList.setCellFactory(list -> draggableFieldCell());
        VBox.setVgrow(fieldList, Priority.ALWAYS);

        Button add = new Button("Add Field");
        Button edit = new Button("Edit");
        Button remove = new Button("Remove");
        Button up = new Button("Move Up");
        Button down = new Button("Move Down");
        edit.disableProperty().bind(fieldList.getSelectionModel().selectedItemProperty().isNull());
        remove.disableProperty().bind(edit.disableProperty());
        up.disableProperty().bind(edit.disableProperty());
        down.disableProperty().bind(edit.disableProperty());

        add.setOnAction(event -> new FieldEditorDialog(stage, settings, null).showAndWait().ifPresent(field -> {
            field.setPosition(fields.size());
            fields.add(field);
        }));
        edit.setOnAction(event -> editSelectedField());
        fieldList.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && fieldList.getSelectionModel().getSelectedItem() != null) {
                editSelectedField();
            }
        });
        remove.setOnAction(event -> {
            FieldDefinition selected = fieldList.getSelectionModel().getSelectedItem();
            if (selected != null && UiUtil.confirm(stage, "Remove Field",
                    "Remove the field “" + selected.name() + "” from this new database?")) {
                fields.remove(selected);
                normalizeFieldPositions();
            }
        });
        up.setOnAction(event -> moveSelected(-1));
        down.setOnAction(event -> moveSelected(1));

        HBox toolbar = new HBox(8, add, edit, remove, up, down);
        content.getChildren().addAll(toolbar, fieldList);
        return content;
    }

    private javafx.scene.Node preferencesPage() {
        VBox content = new VBox(14);
        content.getStyleClass().add("panel-card");
        content.getChildren().addAll(UiUtil.title("Database Preferences"),
                subtitle("These settings are stored inside this .pindb file."));
        GridPane grid = formGrid();
        addFormRow(grid, "Default view", defaultView, 0);
        addFormRow(grid, "Backups to keep", backupLimit, 1);
        Label note = subtitle("PinDB stores up to this many timestamped logical backups inside the database file. "
                + "A separate untouched copy is created automatically before future schema migrations.");
        note.setWrapText(true);
        grid.add(note, 1, 2);
        content.getChildren().add(grid);
        return content;
    }

    private javafx.scene.Node reviewPage() {
        VBox content = new VBox(14);
        content.getStyleClass().add("panel-card");
        content.getChildren().addAll(UiUtil.title("Review and Create"),
                subtitle("Review the setup before PinDB creates the database."));
        review.setWrapText(true);
        review.setStyle("-fx-font-family: monospace;");
        content.getChildren().add(review);
        return content;
    }

    private void showPage(int index) {
        page = Math.max(0, Math.min(pageNodes.size() - 1, index));
        for (int i = 0; i < pageNodes.size(); i++) {
            pageNodes.get(i).setVisible(i == page);
            pageNodes.get(i).setManaged(i == page);
        }
        stepList.getSelectionModel().select(page);
        back.setDisable(page == 0);
        next.setText(page == pageNodes.size() - 1 ? "Create Database" : "Next");
        error.setText("");
        if (page == pageNodes.size() - 1) {
            updateReview();
        }
    }

    private void advance() {
        String validation = validatePage(page);
        if (!validation.isBlank()) {
            error.setText(validation);
            return;
        }
        if (page < pageNodes.size() - 1) {
            showPage(page + 1);
            return;
        }
        createDatabase();
    }

    private String validatePage(int index) {
        if (index == 0) {
            if (databaseName.getText().isBlank()) {
                return "Enter a database name.";
            }
            if (destination.getText().isBlank()) {
                return "Choose where the database should be saved.";
            }
            if (!destination.getText().toLowerCase().endsWith(".pindb")) {
                destination.setText(destination.getText() + ".pindb");
            }
        } else if (index == 1) {
            if (fields.isEmpty()) {
                return "Add at least one field.";
            }
            long distinctNames = fields.stream().map(field -> field.name().toLowerCase()).distinct().count();
            if (distinctNames != fields.size()) {
                return "Every field must have a unique name.";
            }
        }
        return "";
    }

    private void createDatabase() {
        try {
            Path path = Path.of(destination.getText()).toAbsolutePath().normalize();
            if (java.nio.file.Files.exists(path)) {
                error.setText("A file already exists at that location.");
                return;
            }
            normalizeFieldPositions();
            try (DatabaseService ignored = DatabaseService.create(path, databaseName.getText(),
                    description.getText(), List.copyOf(fields), defaultView.getValue(), backupLimit.getValue())) {
                result.set(path);
            }
            stage.close();
        } catch (RuntimeException exception) {
            UiUtil.error(stage, "Could Not Create Database", exception.getMessage(), exception);
        }
    }

    private void updateReview() {
        StringBuilder text = new StringBuilder();
        text.append("Name: ").append(databaseName.getText()).append('\n');
        text.append("File: ").append(destination.getText()).append('\n');
        text.append("Default view: ").append(defaultView.getValue()).append('\n');
        text.append("Internal backups: ").append(backupLimit.getValue()).append("\n\nFields:\n");
        for (int i = 0; i < fields.size(); i++) {
            FieldDefinition field = fields.get(i);
            text.append(i + 1).append(". ").append(field.name()).append(" — ").append(field.type().displayName());
            if (field.required()) {
                text.append(" (required)");
            }
            if (field.summaryType() != org.pindb.model.SummaryType.NONE) {
                text.append(" [summary: ").append(field.summaryType().displayName()).append(']');
            }
            text.append('\n');
        }
        review.setText(text.toString());
    }

    private void chooseDestination() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save New PinDB Database");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PinDB Database", "*.pindb"));
        chooser.setInitialFileName(safeFileName(databaseName.getText()) + ".pindb");
        java.io.File file = chooser.showSaveDialog(stage);
        if (file != null) {
            destination.setText(file.toPath().toString());
        }
    }

    private void suggestDestination() {
        if (destination.getText().isBlank()) {
            destination.setText(Path.of(System.getProperty("user.home"), safeFileName(databaseName.getText()) + ".pindb").toString());
        }
    }

    private void editSelectedField() {
        FieldDefinition selected = fieldList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        int index = fields.indexOf(selected);
        new FieldEditorDialog(stage, settings, selected).showAndWait().ifPresent(updated -> {
            updated.setPosition(index);
            fields.set(index, updated);
            fieldList.getSelectionModel().select(index);
        });
    }

    private ListCell<FieldDefinition> draggableFieldCell() {
        ListCell<FieldDefinition> cell = new ListCell<>() {
            @Override
            protected void updateItem(FieldDefinition item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : (getIndex() + 1) + ".  " + item);
            }
        };
        cell.setOnDragDetected(event -> {
            if (!cell.isEmpty()) {
                Dragboard board = cell.startDragAndDrop(TransferMode.MOVE);
                ClipboardContent content = new ClipboardContent();
                content.putString(Integer.toString(cell.getIndex()));
                board.setContent(content);
                event.consume();
            }
        });
        cell.setOnDragOver(event -> {
            if (event.getGestureSource() != cell && event.getDragboard().hasString()) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            event.consume();
        });
        cell.setOnDragDropped(event -> {
            Dragboard board = event.getDragboard();
            if (board.hasString()) {
                int source = Integer.parseInt(board.getString());
                int target = cell.isEmpty() ? fields.size() : cell.getIndex();
                FieldDefinition moved = fields.remove(source);
                if (target > source) {
                    target--;
                }
                fields.add(Math.max(0, Math.min(target, fields.size())), moved);
                normalizeFieldPositions();
                fieldList.getSelectionModel().select(moved);
                event.setDropCompleted(true);
            }
            event.consume();
        });
        return cell;
    }

    private void moveSelected(int direction) {
        int index = fieldList.getSelectionModel().getSelectedIndex();
        int target = index + direction;
        if (index < 0 || target < 0 || target >= fields.size()) {
            return;
        }
        FieldDefinition selected = fields.remove(index);
        fields.add(target, selected);
        normalizeFieldPositions();
        fieldList.getSelectionModel().select(target);
    }

    private void normalizeFieldPositions() {
        for (int index = 0; index < fields.size(); index++) {
            fields.get(index).setPosition(index);
        }
        fieldList.refresh();
    }

    private GridPane formGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(14);
        return grid;
    }

    private void addFormRow(GridPane grid, String label, javafx.scene.Node node, int row) {
        grid.add(new Label(label + ":"), 0, row);
        grid.add(node, 1, row);
        GridPane.setHgrow(node, Priority.ALWAYS);
    }

    private Label subtitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("subtitle-label");
        label.setWrapText(true);
        return label;
    }

    private static String safeFileName(String value) {
        String safe = value == null ? "database" : value.trim().toLowerCase()
                .replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("^-+|-+$", "");
        return safe.isBlank() ? "database" : safe;
    }
}
