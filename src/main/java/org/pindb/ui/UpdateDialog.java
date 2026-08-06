package org.pindb.ui;

import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.pindb.AppVersion;
import org.pindb.platform.LinuxDistribution;
import org.pindb.service.ReleaseInfo;
import org.pindb.service.SettingsService;

public final class UpdateDialog extends Dialog<UpdateDialog.Action> {
    public enum Action { UPDATE, REMIND_LATER, CANCEL }

    public UpdateDialog(Window owner, SettingsService settings, ReleaseInfo release) {
        initOwner(owner);
        setTitle("PinDB Update Available");
        setHeaderText("PinDB " + release.tag() + " is available");
        ButtonType update = new ButtonType("Download and Update", ButtonBar.ButtonData.OK_DONE);
        ButtonType remind = new ButtonType("Remind Me Later", ButtonBar.ButtonData.OTHER);
        getDialogPane().getButtonTypes().addAll(update, remind, ButtonType.CANCEL);

        Label version = new Label("Installed: " + AppVersion.VERSION + "    Available: "
                + release.version().normalized() + (release.prerelease() ? " (pre-release)" : ""));
        version.getStyleClass().add("section-title");
        LinuxDistribution distribution = LinuxDistribution.current();
        String packageName = release.packageAsset().type().extension();
        String updateMessage = distribution.immutable()
                ? "This Fedora Atomic system requires a manual rpm-ostree installation and reboot."
                : "PinDB will install the matching " + packageName
                + " package after administrator approval, then close and reopen.";
        Label note = new Label(updateMessage);
        note.setWrapText(true);
        note.getStyleClass().add("subtitle-label");
        MarkdownPane markdown = new MarkdownPane(release.markdownNotes());
        markdown.setPrefSize(720, 440);
        VBox content = new VBox(10, version, note, markdown);
        getDialogPane().setContent(content);
        getDialogPane().setPrefWidth(780);
        setResultConverter(button -> button == update ? Action.UPDATE
                : button == remind ? Action.REMIND_LATER : Action.CANCEL);
        getDialogPane().sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene != null) {
                UiUtil.applyStyles(newScene, settings);
            }
        });
    }
}
