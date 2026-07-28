package org.pindb.ui;

import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.pindb.service.SettingsService;

public final class ReleaseNotesDialog extends Dialog<Void> {
    public ReleaseNotesDialog(Window owner, SettingsService settings, String tag, String markdown) {
        initOwner(owner);
        setTitle("PinDB Updated");
        setHeaderText("What changed in PinDB " + tag);
        setResizable(true);
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        String notesText = markdown == null || markdown.isBlank()
                ? "No release notes were provided for this update."
                : markdown;

        VBox content = new VBox();
        try {
            MarkdownPane notes = new MarkdownPane(notesText);
            notes.setFitToWidth(true);
            notes.setMinSize(680, 420);
            notes.setPrefSize(760, 540);
            notes.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            VBox.setVgrow(notes, Priority.ALWAYS);
            content.getChildren().add(notes);
        } catch (RuntimeException exception) {
            TextArea fallback = new TextArea(notesText);
            fallback.setEditable(false);
            fallback.setWrapText(true);
            fallback.setMinSize(680, 420);
            fallback.setPrefSize(760, 540);
            fallback.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            VBox.setVgrow(fallback, Priority.ALWAYS);
            content.getChildren().add(fallback);
        }

        content.setMinSize(680, 420);
        content.setPrefSize(760, 540);
        getDialogPane().setContent(content);
        getDialogPane().setMinSize(720, 520);
        getDialogPane().setPrefSize(800, 640);

        getDialogPane().sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene != null) {
                UiUtil.applyStyles(newScene, settings);
            }
        });
    }
}
