package org.pindb.ui;

import javafx.collections.FXCollections;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;
import org.pindb.service.SettingsService;

public final class SettingsDialog extends Dialog<Boolean> {
    public SettingsDialog(Window owner, SettingsService settings) {
        initOwner(owner);
        setTitle("PinDB Settings");
        setHeaderText("Launcher, update, and appearance settings");
        ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

        CheckBox autoUpdates = new CheckBox("Check for updates when PinDB opens");
        autoUpdates.setSelected(settings.autoCheckUpdates());
        CheckBox prereleases = new CheckBox("Include pre-release versions such as 0.2-beta.1");
        prereleases.setSelected(settings.includePrereleases());
        CheckBox autoOpen = new CheckBox("Open the last database automatically");
        autoOpen.setSelected(settings.autoOpenLastDatabase());
        ComboBox<SettingsService.Theme> theme = new ComboBox<>(FXCollections.observableArrayList(SettingsService.Theme.values()));
        theme.setValue(settings.theme());
        theme.setMaxWidth(Double.MAX_VALUE);

        Label prereleaseWarning = new Label("Pre-release versions may be unfinished or unstable. This option is off by default.");
        prereleaseWarning.setWrapText(true);
        prereleaseWarning.getStyleClass().add("muted-label");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.add(autoUpdates, 0, 0, 2, 1);
        grid.add(prereleases, 0, 1, 2, 1);
        grid.add(prereleaseWarning, 0, 2, 2, 1);
        grid.add(autoOpen, 0, 3, 2, 1);
        grid.add(new Label("Theme:"), 0, 4);
        grid.add(theme, 1, 4);
        getDialogPane().setContent(grid);
        getDialogPane().setPrefWidth(600);

        setResultConverter(button -> {
            if (button != save) {
                return false;
            }
            settings.setAutoCheckUpdates(autoUpdates.isSelected());
            settings.setIncludePrereleases(prereleases.isSelected());
            settings.setAutoOpenLastDatabase(autoOpen.isSelected());
            settings.setTheme(theme.getValue());
            settings.clearUpdateSnooze();
            return true;
        });
        getDialogPane().sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene != null) {
                UiUtil.applyStyles(newScene, settings);
            }
        });
    }
}
