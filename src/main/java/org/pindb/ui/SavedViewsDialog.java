package org.pindb.ui;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.pindb.db.DatabaseException;
import org.pindb.db.DatabaseService;
import org.pindb.model.FieldDefinition;
import org.pindb.model.SavedView;
import org.pindb.service.SettingsService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class SavedViewsDialog {
    private final Stage stage = new Stage();
    private final SettingsService settings;
    private final DatabaseService database;
    private final Supplier<SavedView> currentView;
    private final Consumer<SavedView> onApply;
    private final Runnable onChanged;
    private final ListView<SavedView> list = new ListView<>();
    private Map<Long, FieldDefinition> fieldsById = Map.of();

    public SavedViewsDialog(Window owner,
                            SettingsService settings,
                            DatabaseService database,
                            Supplier<SavedView> currentView,
                            Consumer<SavedView> onApply,
                            Runnable onChanged) {
        this.settings = settings;
        this.database = database;
        this.currentView = currentView;
        this.onApply = onApply;
        this.onChanged = onChanged;

        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("Saved Views");

        Label description = new Label(
                "Saved views keep a reusable search, filter, and sort setup inside this database.");
        description.setWrapText(true);
        description.getStyleClass().add("subtitle-label");

        list.setPlaceholder(new Label("No saved views yet."));
        list.setCellFactory(ignored -> new ListCell<>() {
            @Override
            protected void updateItem(SavedView item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                Label name = new Label(item.name());
                name.getStyleClass().add("section-title");
                List<String> problems = item.validationProblems(fieldsById);
                String detail = problems.isEmpty() ? summary(item) : "Needs attention — " + problems.getFirst();
                Label metadata = new Label(detail);
                metadata.setWrapText(true);
                metadata.getStyleClass().add(problems.isEmpty() ? "muted-label" : "error-label");
                setGraphic(new VBox(3, name, metadata));
                setText(null);
            }
        });
        VBox.setVgrow(list, Priority.ALWAYS);

        Button apply = UiUtil.primaryButton("Apply");
        Button rename = new Button("Rename");
        Button update = new Button("Update from Current");
        Button delete = new Button("Delete");
        delete.getStyleClass().add("danger");
        Button done = new Button("Done");

        apply.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        rename.disableProperty().bind(apply.disableProperty());
        update.disableProperty().bind(apply.disableProperty());
        delete.disableProperty().bind(apply.disableProperty());

        apply.setOnAction(event -> {
            SavedView selected = list.getSelectionModel().getSelectedItem();
            if (selected != null) {
                onApply.accept(selected);
                stage.close();
            }
        });
        rename.setOnAction(event -> renameSelected());
        update.setOnAction(event -> updateSelected());
        delete.setOnAction(event -> deleteSelected());
        done.setOnAction(event -> stage.close());

        HBox leftActions = new HBox(8, apply, rename, update, delete);
        BorderPane footer = new BorderPane();
        footer.setLeft(leftActions);
        footer.setRight(done);

        VBox root = new VBox(12, description, list, footer);
        root.setPadding(new Insets(14));
        Scene scene = new Scene(root, 760, 560);
        UiUtil.applyStyles(scene, settings);
        stage.setScene(scene);

        reload();
    }

    public void showAndWait() {
        stage.showAndWait();
    }

    private void renameSelected() {
        SavedView selected = list.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        TextInputDialog dialog = new TextInputDialog(selected.name());
        dialog.initOwner(stage);
        dialog.setTitle("Rename Saved View");
        dialog.setHeaderText("Rename “" + selected.name() + "”");
        dialog.setContentText("Name:");
        dialog.getDialogPane().sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene != null) {
                UiUtil.applyStyles(newScene, settings);
            }
        });
        String name = dialog.showAndWait().map(String::trim).orElse("");
        if (name.isBlank() || name.equals(selected.name())) {
            return;
        }
        try {
            database.updateSavedView(new SavedView(selected.id(), name, selected.searchText(),
                    selected.filterSpec(), selected.sortOrder()));
            changed(selected.id());
        } catch (DatabaseException exception) {
            UiUtil.error(stage, "Could Not Rename Saved View", exception.getMessage(), exception);
        }
    }

    private void updateSelected() {
        SavedView selected = list.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        SavedView current = currentView.get();
        try {
            database.updateSavedView(new SavedView(selected.id(), selected.name(), current.searchText(),
                    current.filterSpec(), current.sortOrder()));
            changed(selected.id());
        } catch (DatabaseException exception) {
            UiUtil.error(stage, "Could Not Update Saved View", exception.getMessage(), exception);
        }
    }

    private void deleteSelected() {
        SavedView selected = list.getSelectionModel().getSelectedItem();
        if (selected == null || !UiUtil.confirm(stage, "Delete Saved View",
                "Delete the saved view “" + selected.name() + "”?")) {
            return;
        }
        try {
            database.deleteSavedView(selected.id());
            changed(null);
        } catch (DatabaseException exception) {
            UiUtil.error(stage, "Could Not Delete Saved View", exception.getMessage(), exception);
        }
    }

    private void changed(String selectedId) {
        reload();
        if (selectedId != null) {
            list.getItems().stream().filter(view -> view.id().equals(selectedId)).findFirst()
                    .ifPresent(list.getSelectionModel()::select);
        }
        onChanged.run();
    }

    private void reload() {
        LinkedHashMap<Long, FieldDefinition> map = new LinkedHashMap<>();
        database.fields().forEach(field -> map.put(field.id(), field));
        fieldsById = Map.copyOf(map);
        list.setItems(FXCollections.observableArrayList(database.savedViews()));
    }

    private static String summary(SavedView view) {
        int parts = 0;
        if (!view.searchText().isBlank()) {
            parts++;
        }
        if (!view.filterSpec().isEmpty()) {
            parts++;
        }
        if (!view.sortOrder().isEmpty()) {
            parts++;
        }
        return switch (parts) {
            case 0 -> "No search, filter, or sort settings";
            case 1 -> view.searchText().isBlank()
                    ? (view.filterSpec().isEmpty() ? "Sort settings saved" : "Filter settings saved")
                    : "Search settings saved";
            default -> "Search/filter/sort settings saved";
        };
    }
}
