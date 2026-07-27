package org.pindb.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.pindb.db.DatabaseService;
import org.pindb.model.FieldDefinition;
import org.pindb.service.SettingsService;

import java.util.List;

public final class ManageFieldsDialog {
    private final Stage stage = new Stage();
    private final DatabaseService database;
    private final SettingsService settings;
    private final Runnable onChanged;
    private final ObservableList<FieldDefinition> fields = FXCollections.observableArrayList();
    private final ListView<FieldDefinition> list = new ListView<>(fields);

    public ManageFieldsDialog(Window owner, SettingsService settings, DatabaseService database, Runnable onChanged) {
        this.database = database;
        this.settings = settings;
        this.onChanged = onChanged;
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("Manage Database Fields");

        Label warning = new Label("Fields can be added, edited, and rearranged. Deleting a field permanently removes "
                + "its values from all entries, although the database's internal backups can still restore an earlier state.");
        warning.setWrapText(true);
        warning.getStyleClass().add("subtitle-label");

        list.setCellFactory(ignored -> draggableCell());
        VBox.setVgrow(list, Priority.ALWAYS);
        reload();

        Button add = new Button("Add Field");
        Button edit = new Button("Edit");
        Button remove = new Button("Delete Field");
        Button up = new Button("Move Up");
        Button down = new Button("Move Down");
        Button close = UiUtil.primaryButton("Done");

        edit.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        remove.disableProperty().bind(edit.disableProperty());
        up.disableProperty().bind(edit.disableProperty());
        down.disableProperty().bind(edit.disableProperty());

        add.setOnAction(event -> new FieldEditorDialog(stage, settings, null).showAndWait().ifPresent(field -> {
            database.addField(field);
            changed();
        }));
        edit.setOnAction(event -> editSelected());
        list.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                editSelected();
            }
        });
        remove.setOnAction(event -> deleteSelected());
        up.setOnAction(event -> move(-1));
        down.setOnAction(event -> move(1));
        close.setOnAction(event -> stage.close());

        HBox toolbar = new HBox(8, add, edit, remove, up, down);
        BorderPane footer = new BorderPane();
        footer.setRight(close);
        VBox root = new VBox(12, warning, toolbar, list, footer);
        root.setPadding(new Insets(14));
        Scene scene = new Scene(root, 700, 580);
        UiUtil.applyStyles(scene, settings);
        stage.setScene(scene);
    }

    public void showAndWait() {
        stage.showAndWait();
    }

    private void editSelected() {
        FieldDefinition selected = list.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        new FieldEditorDialog(stage, settings, selected).showAndWait().ifPresent(updated -> {
            database.updateField(updated);
            changed();
            list.getSelectionModel().select(updated);
        });
    }

    private void deleteSelected() {
        FieldDefinition selected = list.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        boolean confirmed = UiUtil.confirm(stage, "Delete Field and Its Data",
                "Deleting “" + selected.name() + "” will remove every stored value for this field from the current database. "
                        + "This cannot be undone except by restoring an internal backup. Continue?");
        if (confirmed) {
            database.deleteField(selected.id());
            changed();
        }
    }

    private void move(int direction) {
        int index = list.getSelectionModel().getSelectedIndex();
        int target = index + direction;
        if (index < 0 || target < 0 || target >= fields.size()) {
            return;
        }
        FieldDefinition selected = fields.remove(index);
        fields.add(target, selected);
        saveOrder();
        list.getSelectionModel().select(target);
    }

    private void saveOrder() {
        for (int index = 0; index < fields.size(); index++) {
            fields.get(index).setPosition(index);
        }
        database.reorderFields(List.copyOf(fields));
        onChanged.run();
        list.refresh();
    }

    private void changed() {
        reload();
        onChanged.run();
    }

    private void reload() {
        fields.setAll(database.fields());
    }

    private ListCell<FieldDefinition> draggableCell() {
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
            if (event.getDragboard().hasString()) {
                int source = Integer.parseInt(event.getDragboard().getString());
                int target = cell.isEmpty() ? fields.size() : cell.getIndex();
                FieldDefinition moved = fields.remove(source);
                if (target > source) {
                    target--;
                }
                fields.add(Math.max(0, Math.min(target, fields.size())), moved);
                saveOrder();
                list.getSelectionModel().select(moved);
                event.setDropCompleted(true);
            }
            event.consume();
        });
        return cell;
    }
}
