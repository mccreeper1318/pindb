package org.pindb.ui;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.pindb.service.ReleaseHistoryService;
import org.pindb.service.ReleaseNote;
import org.pindb.service.SettingsService;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class UpdateHistoryPane extends BorderPane {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, uuuu")
            .withZone(ZoneId.systemDefault());
    private final ReleaseHistoryService history = new ReleaseHistoryService();
    private final ListView<ReleaseNote> versions = new ListView<>();
    private final MarkdownPane notes = new MarkdownPane("Select a PinDB version to view its release notes.");
    private final Label status = new Label();

    public UpdateHistoryPane(SettingsService settings) {
        setPadding(new Insets(8));
        versions.setPrefWidth(235);
        versions.setCellFactory(ignored -> new ListCell<>() {
            @Override
            protected void updateItem(ReleaseNote item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                Label version = new Label(item.displayName());
                version.setStyle("-fx-font-weight: bold;");
                Label detail = new Label(item.publishedAt().getEpochSecond() > 0
                        ? DATE_FORMAT.format(item.publishedAt())
                        : item.bundled() ? "Bundled with PinDB" : "Release date unavailable");
                detail.getStyleClass().add("muted-label");
                setGraphic(new VBox(2, version, detail));
                setText(null);
            }
        });
        versions.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, selected) -> {
            if (selected != null) {
                String header = "# " + selected.title() + "\n\n"
                        + (selected.prerelease() ? "**Pre-release**\n\n" : "")
                        + selected.markdown();
                notes.render(header);
            }
        });

        Button refresh = new Button("Refresh from GitHub");
        refresh.setOnAction(event -> refresh(refresh));
        status.setWrapText(true);
        status.getStyleClass().add("muted-label");
        VBox left = new VBox(8,
                new Label("Version History"), versions, refresh, status);
        VBox.setVgrow(versions, Priority.ALWAYS);
        left.setPadding(new Insets(0, 10, 0, 0));
        setLeft(left);
        setCenter(notes);
        load(history.loadCachedOrBundled());
    }

    private void refresh(Button button) {
        button.setDisable(true);
        status.setText("Loading releases from GitHub…");
        Task<List<ReleaseNote>> task = new Task<>() {
            @Override
            protected List<ReleaseNote> call() throws Exception {
                return history.refresh();
            }
        };
        task.setOnSucceeded(event -> {
            button.setDisable(false);
            status.setText("Release history updated.");
            load(task.getValue());
        });
        task.setOnFailed(event -> {
            button.setDisable(false);
            status.setText("Could not refresh. Showing cached and bundled release notes.");
        });
        Thread thread = new Thread(task, "pindb-release-history");
        thread.setDaemon(true);
        thread.start();
    }

    private void load(List<ReleaseNote> releases) {
        ReleaseNote selected = versions.getSelectionModel().getSelectedItem();
        versions.getItems().setAll(releases);
        if (selected != null) {
            versions.getItems().stream().filter(note -> note.tag().equals(selected.tag()))
                    .findFirst().ifPresent(versions.getSelectionModel()::select);
        }
        if (versions.getSelectionModel().isEmpty() && !versions.getItems().isEmpty()) {
            Platform.runLater(() -> versions.getSelectionModel().selectFirst());
        }
    }
}
