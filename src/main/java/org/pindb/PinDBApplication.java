package org.pindb;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.pindb.service.SettingsService;
import org.pindb.service.UpdateInstaller;
import org.pindb.ui.LauncherWindow;
import org.pindb.ui.ReleaseNotesDialog;
import org.pindb.ui.UiUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class PinDBApplication extends Application {
    private AppContext context;

    @Override
    public void start(Stage ignoredPrimaryStage) {
        context = new AppContext();
        LauncherWindow launcher = new LauncherWindow(context);
        context.setLauncher(launcher);
        launcher.showAndFocus();

        List<String> arguments = getParameters().getRaw();
        Platform.runLater(() -> handleStartup(arguments));
    }

    private void handleStartup(List<String> arguments) {
        String updatedTag = valueOf(arguments, "--updated-tag=");
        String notesPath = valueOf(arguments, "--updated-notes=");
        String failedPath = valueOf(arguments, "--update-failed=");

        if (failedPath != null && !failedPath.isBlank()) {
            UpdateInstaller.showFailedInstallPrompt(context.launcher().stage(), Path.of(failedPath));
        }
        if (updatedTag != null && notesPath != null) {
            Path notes = Path.of(notesPath);
            String markdown = "";
            try {
                if (Files.isRegularFile(notes)) {
                    markdown = Files.readString(notes, StandardCharsets.UTF_8);
                    Files.deleteIfExists(notes);
                }
            } catch (IOException exception) {
                UiUtil.error(context.launcher().stage(), "Release Notes Unavailable",
                        "PinDB was updated, but its release notes could not be read.", exception);
            }
            new ReleaseNotesDialog(context.launcher().stage(), context.settings(), updatedTag, markdown).showAndWait();
        }

        Path requestedDatabase = arguments.stream()
                .filter(argument -> !argument.startsWith("--"))
                .map(Path::of)
                .filter(Files::isRegularFile)
                .findFirst().orElse(null);
        if (requestedDatabase != null) {
            context.openDatabase(requestedDatabase);
        } else if (context.settings().autoOpenLastDatabase()) {
            Path last = context.settings().lastDatabase();
            if (last != null) {
                context.openDatabase(last);
            }
        }

        context.checkForUpdates(context.launcher().stage(), false);
    }

    private static String valueOf(List<String> arguments, String prefix) {
        return arguments.stream().filter(argument -> argument.startsWith(prefix))
                .map(argument -> argument.substring(prefix.length())).findFirst().orElse(null);
    }

    @Override
    public void stop() {
        if (context != null) {
            // Database windows close their own connections. JavaFX invokes this after all windows are closed.
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
