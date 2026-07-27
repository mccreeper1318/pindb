package org.pindb;

import javafx.application.Platform;
import javafx.stage.Window;
import org.pindb.db.DatabaseService;
import org.pindb.service.ReleaseInfo;
import org.pindb.service.SettingsService;
import org.pindb.service.UpdateInstaller;
import org.pindb.service.UpdateService;
import org.pindb.ui.DatabaseWindow;
import org.pindb.ui.LauncherWindow;
import org.pindb.ui.ReleaseNotesDialog;
import org.pindb.ui.UiUtil;
import org.pindb.ui.UpdateDialog;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class AppContext {
    private final SettingsService settings = new SettingsService();
    private final UpdateService updateService = new UpdateService();
    private final Map<Path, DatabaseWindow> databaseWindows = new LinkedHashMap<>();
    private LauncherWindow launcher;

    public SettingsService settings() {
        return settings;
    }

    public void setLauncher(LauncherWindow launcher) {
        this.launcher = launcher;
    }

    public LauncherWindow launcher() {
        return launcher;
    }

    public void openDatabase(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        DatabaseWindow existing = databaseWindows.get(normalized);
        if (existing != null) {
            existing.showAndFocus();
            return;
        }
        try {
            DatabaseService database = DatabaseService.open(normalized);
            DatabaseWindow window = new DatabaseWindow(this, database, () -> {
                databaseWindows.remove(normalized);
                if (databaseWindows.isEmpty() && launcher != null) {
                    launcher.showAndFocus();
                }
            });
            databaseWindows.put(normalized, window);
            settings.addRecentFile(normalized);
            if (launcher != null) {
                launcher.refreshRecentFiles();
                launcher.hide();
            }
            window.showAndFocus();
        } catch (RuntimeException exception) {
            settings.removeRecentFile(normalized);
            UiUtil.error(launcher == null ? null : launcher.stage(), "Could Not Open Database",
                    "PinDB could not open “" + normalized.getFileName() + "”.", exception);
            if (launcher != null) {
                launcher.refreshRecentFiles();
                launcher.showAndFocus();
            }
        }
    }

    public void closeAll() {
        for (DatabaseWindow window : databaseWindows.values().toArray(DatabaseWindow[]::new)) {
            window.close();
        }
        Platform.exit();
    }

    public void refreshStyles() {
        if (launcher != null) {
            launcher.refreshStyle();
        }
        databaseWindows.values().forEach(DatabaseWindow::refreshStyle);
    }

    public void checkForUpdates(Window owner, boolean manual) {
        if (!manual && (!settings.autoCheckUpdates() || !settings.updateCheckAllowedNow())) {
            return;
        }
        if (launcher != null) {
            launcher.setUpdateStatus("Checking for updates…");
        }
        CompletableFuture.supplyAsync(() -> {
            try {
                return updateService.checkForUpdate(settings.includePrereleases());
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        }).whenComplete((result, failure) -> Platform.runLater(() -> {
            if (failure != null) {
                if (launcher != null) {
                    launcher.setUpdateStatus("Update check failed");
                }
                if (manual) {
                    UiUtil.error(owner, "Update Check Failed",
                            "PinDB could not check GitHub Releases for updates.", unwrap(failure));
                }
                return;
            }
            Optional<ReleaseInfo> release = result;
            if (release.isEmpty()) {
                if (launcher != null) {
                    launcher.setUpdateStatus("PinDB " + AppVersion.VERSION + " is up to date");
                }
                if (manual) {
                    UiUtil.information(owner, "No Updates Available",
                            "PinDB " + AppVersion.VERSION + " is the newest available version for your update channel.");
                }
                return;
            }
            ReleaseInfo available = release.get();
            if (launcher != null) {
                launcher.setUpdateStatus("Update " + available.tag() + " is available");
            }
            UpdateDialog.Action action = new UpdateDialog(owner, settings, available)
                    .showAndWait().orElse(UpdateDialog.Action.CANCEL);
            if (action == UpdateDialog.Action.REMIND_LATER) {
                settings.snoozeUpdatesForHours(24);
            } else if (action == UpdateDialog.Action.UPDATE) {
                new UpdateInstaller(settings).downloadAndInstall(owner, available);
            }
        }));
    }

    public void showReleaseNotes(String tag, String markdown) {
        if (launcher != null) {
            new ReleaseNotesDialog(launcher.stage(), settings, tag, markdown).showAndWait();
        }
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && (current instanceof java.util.concurrent.CompletionException
                || current instanceof RuntimeException)) {
            current = current.getCause();
        }
        return current;
    }
}
