package org.pindb.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.pindb.AppVersion;
import org.pindb.service.SettingsService;
import org.pindb.service.UpdateService;

import java.util.Objects;

public final class AboutDialog extends Dialog<Void> {
    public AboutDialog(Window owner, SettingsService settings) {
        initOwner(owner);
        setTitle("About PinDB");
        setHeaderText(null);
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        ImageView icon = new ImageView(new Image(Objects.requireNonNull(
                getClass().getResourceAsStream("/org/pindb/app/icon-128.png"))));
        icon.setFitWidth(96);
        icon.setFitHeight(96);
        Label title = UiUtil.title("PinDB " + AppVersion.VERSION);
        Label text = new Label("A flexible personal database application built with JavaFX and SQLite.\n\n"
                + "Update repository: " + UpdateService.REPOSITORY + "\n"
                + "Database files use the .pindb extension and remain standard SQLite databases.\n\n"
                + "Current version does not encrypt database contents.");
        text.setWrapText(true);
        text.setMaxWidth(520);
        VBox content = new VBox(12, icon, title, text);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(18));
        getDialogPane().setContent(content);
        getDialogPane().sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene != null) {
                UiUtil.applyStyles(newScene, settings);
            }
        });
    }
}
