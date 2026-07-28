package org.pindb.service;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.pindb.ui.UiUtil;
import org.pindb.util.AppPaths;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
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
import java.time.Instant;
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

        javafx.scene.control.Label status = new javafx.scene.control.Label("Connecting to GitHub…");
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

        Button cancelButton = (Button) dialog.getDialogPane().lookupButton(cancelType);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AtomicBoolean installing = new AtomicBoolean(false);

        Task<DownloadedUpdate> downloadTask = new Task<>() {
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

                Optional<String> expected = fetchExpectedChecksum(
                        release.checksumAsset(), destination.getFileName().toString());
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

        status.textProperty().bind(downloadTask.messageProperty());
        progress.progressProperty().bind(downloadTask.progressProperty());
        dialog.setOnCloseRequest(event -> {
            if (installing.get()) {
                event.consume();
                return;
            }
            cancelled.set(true);
            downloadTask.cancel(true);
        });

        downloadTask.setOnSucceeded(event -> {
            DownloadedUpdate update = downloadTask.getValue();
            status.textProperty().unbind();
            progress.progressProperty().unbind();
            installing.set(true);
            cancelButton.setDisable(true);
            dialog.setTitle("Installing PinDB Update");
            dialog.setHeaderText("Installing " + release.tag());
            status.setText("Waiting for administrator approval…");
            progress.setProgress(-1);

            Task<Void> installTask = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    installPrivileged(update.packageFile(), this::updateMessage);
                    return null;
                }
            };
            status.textProperty().bind(installTask.messageProperty());

            installTask.setOnSucceeded(installEvent -> {
                status.textProperty().unbind();
                installing.set(false);
                dialog.close();
                try {
                    Files.deleteIfExists(update.packageFile());
                } catch (IOException ignored) {
                    // Leave cleanup failures alone after a successful install.
                }
                try {
                    restartAfterUpdate(update.notesFile(), release.tag());
                    Platform.exit();
                } catch (IOException exception) {
                    writeFailureLog(update.packageFile(), exception, "restart");
                    Platform.runLater(() -> UiUtil.error(owner, "Update Installed",
                            "The update installed successfully, but PinDB could not restart automatically. "
                                    + "Open PinDB from the application menu.", exception));
                }
            });

            installTask.setOnFailed(installEvent -> {
                status.textProperty().unbind();
                installing.set(false);
                Throwable failure = installTask.getException();
                Path logFile = writeFailureLog(update.packageFile(), failure, "install");
                dialog.close();
                Platform.runLater(() -> showFailureAlert(owner, update.packageFile(), failure, logFile));
            });

            Thread installThread = new Thread(installTask, "pindb-update-install");
            installThread.setDaemon(true);
            installThread.start();
        });

        downloadTask.setOnCancelled(event -> dialog.close());
        downloadTask.setOnFailed(event -> {
            Throwable failure = downloadTask.getException();
            Path logFile = writeFailureLog(null, failure, "download");
            dialog.close();
            if (!(failure instanceof InterruptedException)) {
                Platform.runLater(() -> showFailureAlert(owner, null, failure, logFile));
            }
        });

        Thread downloadThread = new Thread(downloadTask, "pindb-update-download");
        downloadThread.setDaemon(true);
        downloadThread.start();
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
            throw new IOException("GitHub returned HTTP " + response.statusCode()
                    + " while downloading the update.");
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

    private static void installPrivileged(Path packageFile, MessageReporter messageReporter) throws Exception {
        if (installedLauncher() == null) {
            throw new IOException("Automatic installation is available after PinDB has been installed "
                    + "from its .deb package.");
        }

        Path pkexec = Path.of("/usr/bin/pkexec");
        Path aptGet = Path.of("/usr/bin/apt-get");
        if (!Files.isExecutable(pkexec)) {
            throw new IOException("The pkexec administrator tool is not installed at /usr/bin/pkexec.");
        }
        if (!Files.isExecutable(aptGet)) {
            throw new IOException("The apt-get package installer is not available at /usr/bin/apt-get.");
        }

        Path rootScript = packageFile.getParent().resolve("install-pindb-update-root.sh");
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

                DEBIAN_FRONTEND=noninteractive /usr/bin/apt-get install -y "$DEB"
                STATUS=$?
                if [ "$STATUS" -eq 0 ]; then
                  [ "$HAD_OLD" -eq 1 ] && rm -rf "$BACKUP"
                  exit 0
                fi

                if [ "$HAD_OLD" -eq 1 ]; then
                  rm -rf /opt/pindb
                  mv "$BACKUP" /opt/pindb
                fi
                exit "$STATUS"
                """;
        Files.writeString(rootScript, root, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        try {
            messageReporter.report("Approve the administrator prompt to install the update…");
            ProcessBuilder builder = new ProcessBuilder(pkexec.toString(), "/bin/sh",
                    rootScript.toString(), packageFile.toAbsolutePath().toString());
            builder.redirectErrorStream(true);
            Process process = builder.start();
            String output;
            try (InputStream input = process.getInputStream()) {
                output = new String(input.readAllBytes(), StandardCharsets.UTF_8).trim();
            }
            int status = process.waitFor();
            if (status != 0) {
                String details = output.isBlank() ? "No additional installer output was provided." : output;
                throw new IOException("The administrator installer exited with status " + status
                        + ".\n\n" + details);
            }
        } finally {
            Files.deleteIfExists(rootScript);
        }
    }

    private static void restartAfterUpdate(Path notesFile, String tag) throws IOException {
        Path launcher = installedLauncher();
        if (launcher == null) {
            throw new IOException("The installed PinDB launcher could not be found after the update.");
        }
        new ProcessBuilder(launcher.toString(), "--updated-tag=" + tag,
                "--updated-notes=" + notesFile).start();
    }

    private static Path installedLauncher() {
        Path packaged = Path.of("/opt/pindb/bin/PinDB");
        if (Files.isExecutable(packaged)) {
            return packaged;
        }
        Path system = Path.of("/usr/bin/pindb");
        return Files.isExecutable(system) ? system : null;
    }

    private void showFailureAlert(Window owner, Path downloadedPackage, Throwable failure, Path logFile) {
        StringBuilder message = new StringBuilder(
                "The current PinDB installation was preserved.\n\nCause: ")
                .append(failureSummary(failure));
        if (downloadedPackage != null) {
            message.append("\n\nDownloaded package:\n")
                    .append(downloadedPackage)
                    .append("\n\nManual command:\nsudo apt install \"")
                    .append(downloadedPackage)
                    .append("\"");
        }
        if (logFile != null) {
            message.append("\n\nDiagnostic log:\n").append(logFile);
        }

        try {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            if (owner != null && owner.isShowing()) {
                alert.initOwner(owner);
            }
            alert.setTitle("Update Failed");
            alert.setHeaderText("PinDB could not install the update");
            alert.setContentText(message.toString());
            alert.getDialogPane().setPrefWidth(720);
            alert.getDialogPane().sceneProperty().addListener((observable, oldScene, newScene) -> {
                if (newScene != null) {
                    UiUtil.applyStyles(newScene, settings);
                }
            });
            alert.showAndWait();
        } catch (RuntimeException dialogFailure) {
            System.err.println("PinDB update failed: " + message);
            dialogFailure.printStackTrace(System.err);
        }
    }

    private static Path writeFailureLog(Path downloadedPackage, Throwable failure, String stage) {
        try {
            Path logFile = AppPaths.ensure(AppPaths.stateDirectory()).resolve("update-error.log");
            StringBuilder text = new StringBuilder()
                    .append("PinDB update failure\n")
                    .append("Time: ").append(Instant.now()).append('\n')
                    .append("Stage: ").append(stage).append('\n')
                    .append("Package: ").append(downloadedPackage == null ? "none" : downloadedPackage).append('\n')
                    .append("Cause: ").append(failureSummary(failure)).append("\n\n")
                    .append(stackTrace(failure));
            Files.writeString(logFile, text.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return logFile;
        } catch (RuntimeException | IOException logFailure) {
            logFailure.printStackTrace(System.err);
            return null;
        }
    }

    private static String failureSummary(Throwable failure) {
        if (failure == null) {
            return "Unknown update error.";
        }
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return message == null || message.isBlank()
                ? root.getClass().getSimpleName()
                : message;
    }

    private static String stackTrace(Throwable failure) {
        if (failure == null) {
            return "";
        }
        StringWriter writer = new StringWriter();
        failure.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    public static void showFailedInstallPrompt(Window owner, Path packageFile) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        if (owner != null && owner.isShowing()) {
            alert.initOwner(owner);
        }
        alert.setTitle("PinDB Update Failed");
        alert.setHeaderText("The update could not be installed");
        alert.setContentText("Your previous PinDB installation was preserved. "
                + "The downloaded package is located at:\n" + packageFile
                + "\n\nInstall it manually with:\nsudo apt install \"" + packageFile + "\"");
        alert.getButtonTypes().setAll(ButtonType.CLOSE);
        alert.showAndWait();
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
