package org.pindb.ui;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.pindb.db.DatabaseService;
import org.pindb.model.FieldDefinition;
import org.pindb.model.RecordData;
import org.pindb.service.SettingsService;

import java.util.List;

public final class TrashDialog {
    private final Stage stage = new Stage();
    private final DatabaseService database;
    private final Runnable onChanged;
    private final List<FieldDefinition> fields;
    private final ListView<RecordData> list = new ListView<>();

    public TrashDialog(Window owner, SettingsService settings, DatabaseService database, Runnable onChanged) {
        this.database = database;
        this.onChanged = onChanged;
        this.fields = database.fields();
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("Recently Deleted Entries");

        Label description = new Label("Deleted entries remain here until they are restored or permanently removed.");
        description.setWrapText(true);
        description.getStyleClass().add("subtitle-label");
        list.setCellFactory(ignored -> new ListCell<>() {
            @Override
            protected void updateItem(RecordData item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                VBox card = new VBox(4);
                card.getStyleClass().add("record-card");
                Label id = new Label("Entry " + item.id() + " — deleted " + item.deletedAt());
                id.getStyleClass().add("section-title");
                card.getChildren().add(id);
                fields.stream().limit(4).forEach(field -> card.getChildren().add(
                        new Label(field.name() + ": " + UiUtil.formatValue(field, item.value(field.id())))));
                setGraphic(card);
            }
        });
        VBox.setVgrow(list, Priority.ALWAYS);
        reload();

        Button restore = new Button("Restore");
        Button delete = new Button("Delete Permanently");
        delete.getStyleClass().add("danger");
        Button empty = new Button("Empty Trash");
        Button close = UiUtil.primaryButton("Done");
        restore.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        delete.disableProperty().bind(restore.disableProperty());
        restore.setOnAction(event -> {
            RecordData selected = list.getSelectionModel().getSelectedItem();
            if (selected != null) {
                database.restoreRecord(selected.id());
                changed();
            }
        });
        delete.setOnAction(event -> {
            RecordData selected = list.getSelectionModel().getSelectedItem();
            if (selected != null && UiUtil.confirm(stage, "Permanently Delete Entry",
                    "Permanently delete entry " + selected.id() + "? It can only be recovered by restoring an earlier backup.")) {
                database.permanentlyDeleteRecord(selected.id());
                changed();
            }
        });
        empty.setOnAction(event -> {
            if (!list.getItems().isEmpty() && UiUtil.confirm(stage, "Empty Trash",
                    "Permanently delete every entry in the trash?")) {
                database.emptyTrash();
                changed();
            }
        });
        close.setOnAction(event -> stage.close());

        HBox actions = new HBox(8, restore, delete, empty);
        BorderPane footer = new BorderPane();
        footer.setLeft(actions);
        footer.setRight(close);
        VBox root = new VBox(12, description, list, footer);
        root.setPadding(new Insets(14));
        Scene scene = new Scene(root, 760, 600);
        UiUtil.applyStyles(scene, settings);
        stage.setScene(scene);
    }

    public void showAndWait() {
        stage.showAndWait();
    }

    private void changed() {
        reload();
        onChanged.run();
    }

    private void reload() {
        list.setItems(FXCollections.observableArrayList(database.deletedRecords()));
    }
}
