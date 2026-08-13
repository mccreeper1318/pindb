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
import org.pindb.db.DocumentStore;
import org.pindb.model.BackupSnapshot;
import org.pindb.service.SettingsService;

public final class BackupRestoreDialog {
    private final Stage stage = new Stage();
    private final DatabaseService database;
    private final Runnable onRestored;
    private final ListView<BackupSnapshot> list = new ListView<>();

    public BackupRestoreDialog(Window owner, SettingsService settings, DatabaseService database, Runnable onRestored) {
        this.database = database;
        this.onRestored = onRestored == null ? () -> { } : onRestored;
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("Database Backups");

        Label explanation = new Label("PinDB keeps timestamped logical backups inside each .pindb file. "
                + "Restoring a backup replaces the current fields and entries with the selected snapshot after first saving the current state.");
        explanation.setWrapText(true);
        explanation.getStyleClass().add("subtitle-label");
        list.setCellFactory(ignored -> new ListCell<>() {
            @Override
            protected void updateItem(BackupSnapshot item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.createdAt() + "  —  " + item.reason());
            }
        });
        VBox.setVgrow(list, Priority.ALWAYS);
        reload();

        Button restore = UiUtil.primaryButton("Restore Selected");
        Button delete = new Button("Delete Backup");
        Button close = new Button("Close");
        restore.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        delete.disableProperty().bind(restore.disableProperty());
        restore.setOnAction(event -> {
            BackupSnapshot selected = list.getSelectionModel().getSelectedItem();
            if (selected != null && UiUtil.confirm(stage, "Restore Backup",
                    "Restore the database to " + selected.createdAt() + "? PinDB will first back up the current state.")) {
                database.restoreSnapshot(selected.id());
                try (DocumentStore documents = new DocumentStore(database.path())) {
                    documents.restoreSnapshot(selected.id());
                }
                reload();
                onRestored.run();
                UiUtil.information(stage, "Backup Restored", "The database was restored successfully.");
            }
        });
        delete.setOnAction(event -> {
            BackupSnapshot selected = list.getSelectionModel().getSelectedItem();
            if (selected != null && UiUtil.confirm(stage, "Delete Backup", "Delete this internal backup snapshot?")) {
                database.deleteSnapshot(selected.id());
                reload();
            }
        });
        close.setOnAction(event -> stage.close());

        HBox actions = new HBox(8, restore, delete);
        BorderPane footer = new BorderPane();
        footer.setLeft(actions);
        footer.setRight(close);
        VBox root = new VBox(12, explanation, list, footer);
        root.setPadding(new Insets(14));
        Scene scene = new Scene(root, 780, 560);
        UiUtil.applyStyles(scene, settings);
        stage.setScene(scene);
    }

    public void showAndWait() {
        stage.showAndWait();
    }

    private void reload() {
        list.setItems(FXCollections.observableArrayList(database.backupSnapshots()));
    }
}
