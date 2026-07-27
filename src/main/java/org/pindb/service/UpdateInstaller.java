package org.pindb.service;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.pindb.ui.UiUtil;
import org.pindb.util.AppPaths;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public final class UpdateInstaller {
    private final SettingsService settings;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public UpdateInstaller(SettingsService settings) {
        this.settings = settings;
    }

    public void downloadAndInstall(Window owner, ReleaseInfo release) {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux")) {
            UiUtil.warning(owner, "Automatic Update Unavailable",
                    "Automatic .deb installation is available on Debian-based Linux systems. "
                            + "Download the release manually on this operating system.");
            return;
        }

        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Downloading PinDB Update");
        dialog.setHeaderText("Downloading " + release.tag());
        ButtonType cancelType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().add(cancelType);
        Label status = new Label("Connecting to GitHub…");
        status.setWrapText(true);
        ProgressBar progress = new ProgressBar(-1);
        progress.setMaxWidth(Double.MAX_VALUE);
        dialog.getDialogPane().setContent(new VBox(12, status, progress));
        dialog.getDialogPane().setPrefWidth(520);
        dialog.getDialogPane().sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene != null) {
                UiUtil.applyStyles(newScene, settings);
            }
        });

        AtomicBoolean cancelled = new AtomicBoolean(false);
        Task<DownloadedUpdate> task = new Task<>() {
            @Override
            protected DownloadedUpdate call() throws Exception {
                Path updateDir = AppPaths.ensure(AppPaths.cacheDirectory().resolve("updates"));
                String assetName = Path.of(release.debAsset().getPath()).getFileName().toString();
                if (!assetName.toLowerCase(Locale.ROOT).endsWith(".deb")) {
                    assetName = "pindb-" + release.version().normalized() + ".deb";
                }
                Path destination = updateDir.resolve(assetName);
                Path partial = updateDir.resolve(assetName + ".part");
                Files.deleteIfExists(partial);
                download(release.debAsset(), partial, cancelled, this::updateProgress, this::updateMessage);
                if (cancelled.get()) {
                    throw new InterruptedException("Update download cancelled.");
                }
                Files.move(partial, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

                Optional<String> expected = fetchExpectedChecksum(release.checksumAsset(), destination.getFileName().toString());
                if (expected.isPresent()) {
                    updateMessage("Verifying downloaded package…");
                    String actual = sha256(destination);
                    if (!actual.equalsIgnoreCase(expected.get())) {
                        throw new IOException("The downloaded update failed its SHA-256 verification.");
                    }
                }
                Path notes = updateDir.resolve("release-notes-" + release.version().normalized() + ".md");
                Files.writeString(notes, release.markdownNotes() == null ? "" : release.markdownNotes(),
                        StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                return new DownloadedUpdate(destination, notes);
            }
        };
        status.textProperty().bind(task.messageProperty());
        progress.progressProperty().bind(task.progressProperty());
        dialog.setOnCloseRequest(event -> {
            cancelled.set(true);
            task.cancel(true);
        });
        task.setOnSucceeded(event -> {
            dialog.close();
            DownloadedUpdate update = task.getValue();
            try {
                startPrivilegedInstaller(update.packageFile(), update.notesFile(), release.tag());
                Platform.exit();
            } catch (Exception exception) {
                handleFailure(owner, update.packageFile(), exception);
            }
        });
        task.setOnCancelled(event -> dialog.close());
        task.setOnFailed(event -> {
            dialog.close();
            Throwable failure = task.getException();
            if (!(failure instanceof InterruptedException)) {
                handleFailure(owner, null, failure);
            }
        });
        Thread thread = new Thread(task, "pindb-update-download");
        thread.setDaemon(true);
        thread.start();
        dialog.showAndWait();
    }

    private void download(URI uri, Path destination, AtomicBoolean cancelled,
                          ProgressReporter reporter, MessageReporter messageReporter) throws Exception {
        messageReporter.report("Downloading update package…");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMinutes(10))
                .header("User-Agent", "PinDB-Updater")
                .GET().build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("GitHub returned HTTP " + response.statusCode() + " while downloading the update.");
        }
        long total = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
        try (InputStream input = new BufferedInputStream(response.body());
             var output = Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW)) {
            byte[] buffer = new byte[64 * 1024];
            long received = 0;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (cancelled.get() || Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("Update download cancelled.");
                }
                output.write(buffer, 0, read);
                received += read;
                reporter.report(received, total);
            }
        } catch (Exception exception) {
            Files.deleteIfExists(destination);
            throw exception;
        }
    }

    private Optional<String> fetchExpectedChecksum(URI checksumAsset, String packageName)
            throws IOException, InterruptedException {
        if (checksumAsset == null) {
            return Optional.empty();
        }
        HttpRequest request = HttpRequest.newBuilder(checksumAsset)
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "PinDB-Updater")
                .GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Could not download the update checksum.");
        }
        for (String line : response.body().lines().toList()) {
            String trimmed = line.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            String[] pieces = trimmed.split("\\s+", 2);
            if (pieces.length == 1 || pieces[1].replace("*", "").trim().equals(packageName)) {
                if (pieces[0].matches("(?i)[0-9a-f]{64}")) {
                    return Optional.of(pieces[0]);
                }
            }
        }
        throw new IOException("The checksum file did not contain an entry for " + packageName + ".");
    }

    private static String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void startPrivilegedInstaller(Path packageFile, Path notesFile, String tag) throws IOException {
        if (!Files.isExecutable(Path.of("/opt/pindb/bin/PinDB")) && !Files.isExecutable(Path.of("/usr/bin/pindb"))) {
            throw new IOException("Automatic installation is available after PinDB has been installed from its .deb package.");
        }
        Path updateDir = packageFile.getParent();
        Path rootScript = updateDir.resolve("install-pindb-update-root.sh");
        Path helperScript = updateDir.resolve("install-pindb-update.sh");
        long pid = ProcessHandle.current().pid();

        String root = """
                #!/bin/sh
                set -u
                DEB="$1"
                BACKUP="/opt/pindb-update-backup-$$"
                HAD_OLD=0
                if [ -d /opt/pindb ]; then
                  cp -a /opt/pindb "$BACKUP" || exit 30
                  HAD_OLD=1
                fi
                if /usr/bin/dpkg -i "$DEB"; then
                  [ "$HAD_OLD" -eq 1 ] && rm -rf "$BACKUP"
                  exit 0
                fi
                STATUS=$?
                if [ "$HAD_OLD" -eq 1 ]; then
                  rm -rf /opt/pindb
                  mv "$BACKUP" /opt/pindb
                fi
                exit "$STATUS"
                """;
        String helper = """
                #!/bin/sh
                PID="$1"
                ROOT_SCRIPT="$2"
                DEB="$3"
                NOTES="$4"
                TAG="$5"
                while kill -0 "$PID" 2>/dev/null; do sleep 1; done
                /usr/bin/pkexec "$ROOT_SCRIPT" "$DEB"
                STATUS=$?
                LAUNCHER="/opt/pindb/bin/PinDB"
                [ -x "$LAUNCHER" ] || LAUNCHER="/usr/bin/pindb"
                if [ "$STATUS" -eq 0 ]; then
                  rm -f "$DEB" "$ROOT_SCRIPT"
                  nohup "$LAUNCHER" "--updated-tag=$TAG" "--updated-notes=$NOTES" >/dev/null 2>&1 &
                else
                  rm -f "$NOTES" "$ROOT_SCRIPT"
                  nohup "$LAUNCHER" "--update-failed=$DEB" >/dev/null 2>&1 &
                fi
                rm -f "$0"
                exit 0
                """;
        Files.writeString(rootScript, root, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.writeString(helperScript, helper, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        rootScript.toFile().setExecutable(true, true);
        helperScript.toFile().setExecutable(true, true);
        new ProcessBuilder("/bin/sh", helperScript.toString(), String.valueOf(pid), rootScript.toString(),
                packageFile.toString(), notesFile.toString(), tag).start();
    }

    private static void handleFailure(Window owner, Path downloadedPackage, Throwable failure) {
        String location = downloadedPackage == null ? "" : "\n\nDownloaded package: " + downloadedPackage;
        UiUtil.error(owner, "Update Failed",
                "PinDB could not install the update. The current installation has not been replaced." + location,
                failure);
    }

    public static void showFailedInstallPrompt(Window owner, Path packageFile) {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("PinDB Update Failed");
        dialog.setHeaderText("The update could not be installed");
        ButtonType delete = new ButtonType("Delete Download", ButtonBar.ButtonData.OK_DONE);
        ButtonType keep = new ButtonType("Keep for Manual Install", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(delete, keep);
        Label message = new Label("Your previous PinDB installation was preserved. The downloaded package is located at:\n"
                + packageFile + "\n\nDelete it now or keep it for manual installation.");
        message.setWrapText(true);
        dialog.getDialogPane().setContent(message);
        dialog.setResultConverter(button -> button == delete);
        if (Boolean.TRUE.equals(dialog.showAndWait().orElse(false))) {
            try {
                Files.deleteIfExists(packageFile);
            } catch (IOException exception) {
                UiUtil.error(owner, "Could Not Delete Download", "The update package could not be deleted.", exception);
            }
        }
    }

    private record DownloadedUpdate(Path packageFile, Path notesFile) {
    }

    @FunctionalInterface
    private interface ProgressReporter {
        void report(long complete, long total);
    }

    @FunctionalInterface
    private interface MessageReporter {
        void report(String message);
    }
}
