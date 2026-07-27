package org.pindb.ui;

import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.stage.Window;
import org.pindb.service.SettingsService;

public final class ReleaseNotesDialog extends Dialog<Void> {
    public ReleaseNotesDialog(Window owner, SettingsService settings, String tag, String markdown) {
        initOwner(owner);
        setTitle("PinDB Updated");
        setHeaderText("What changed in PinDB " + tag);
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        MarkdownPane notes = new MarkdownPane(markdown == null || markdown.isBlank()
                ? "No release notes were provided for this update." : markdown);
        notes.setPrefSize(760, 560);
        getDialogPane().setContent(notes);
        getDialogPane().sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene != null) {
                UiUtil.applyStyles(newScene, settings);
            }
        });
    }
}
