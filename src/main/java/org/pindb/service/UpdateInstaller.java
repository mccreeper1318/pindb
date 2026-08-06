package org.pindb.service;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.pindb.platform.LinuxDistribution;
import org.pindb.platform.LinuxPackageType;
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
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
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
        LinuxDistribution distribution = LinuxDistribution.current();
        ReleasePackage releasePackage = release.packageAsset();
        if (!installationSupported(owner, distribution, releasePackage)) {
            return;
        }

        Dialog<Void> dialog = progressDialog(owner, "Downloading PinDB Update", "Downloading " + release.tag());
        Label status = (Label) ((VBox) dialog.getDialogPane().getContent()).getChildren().getFirst();
        ProgressBar progress = (ProgressBar) ((VBox) dialog.getDialogPane().getContent()).getChildren().get(1);
        Button cancel = (Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicBoolean installing = new AtomicBoolean();

        Task<DownloadedUpdate> downloadTask = new Task<>() {
            @Override
            protected DownloadedUpdate call() throws Exception {
                Path directory = AppPaths.ensure(AppPaths.cacheDirectory().resolve("updates"));
                Path destination = directory.resolve(safeAssetName(releasePackage));
                Path partial = destination.resolveSibling(destination.getFileName() + ".part");
                Files.deleteIfExists(partial);
                download(releasePackage.downloadUri(), partial, cancelled, this::updateProgress, this::updateMessage);
                if (cancelled.get()) {
                    throw new InterruptedException("Update download cancelled.");
                }
                Files.move(partial, destination, StandardCopyOption.REPLACE_EXISTING);
                verifyChecksum(destination, releasePackage.checksumUri(), this::updateMessage);
                Path notes = directory.resolve("release-notes-" + release.version().normalized() + ".md");
                Files.writeString(notes, release.markdownNotes() == null ? "" : release.markdownNotes(),
                        StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                return new DownloadedUpdate(destination, notes, releasePackage, distribution);
            }
        };
        taskMessages(downloadTask, status);
        progress.progressProperty().bind(downloadTask.progressProperty());
        dialog.setOnCloseRequest(event -> {
            if (installing.get()) {
                event.consume();
            } else {
                cancelled.set(true);
                downloadTask.cancel(true);
            }
        });

        downloadTask.setOnSucceeded(event -> {
            progress.progressProperty().unbind();
            installing.set(true);
            cancel.setDisable(true);
            dialog.setTitle("Installing PinDB Update");
            dialog.setHeaderText("Installing " + release.tag());
            progress.setProgress(-1);
            install(owner, dialog, status, installing, downloadTask.getValue(), release.tag());
        });
        downloadTask.setOnCancelled(event -> dialog.close());
        downloadTask.setOnFailed(event -> {
            dialog.close();
            Throwable failure = downloadTask.getException();
            if (!(failure instanceof InterruptedException)) {
                Path log = writeFailureLog(null, failure, "download");
                Platform.runLater(() -> showFailureAlert(owner, null, releasePackage.type(), distribution, failure, log));
            }
        });

        Thread thread = new Thread(downloadTask, "pindb-update-download");
        thread.setDaemon(true);
        thread.start();
        dialog.showAndWait();
    }

    private Dialog<Void> progressDialog(Window owner, String title, String header) {
        Dialog<Void> dialog = new Dialog<>();
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
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
        return dialog;
    }

    private static void taskMessages(Task<?> task, Label status) {
        task.messageProperty().addListener((observable, oldMessage, newMessage) -> {
            if (newMessage != null && !newMessage.isBlank()) {
                status.setText(newMessage);
            }
        });
    }

    private void install(Window owner, Dialog<Void> dialog, Label status, AtomicBoolean installing,
                         DownloadedUpdate update, String tag) {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                installPrivileged(update.packageFile(), update.releasePackage().type(), this::updateMessage);
                return null;
            }
        };
        taskMessages(task, status);
        task.setOnSucceeded(event -> {
            installing.set(false);
            dialog.close();
            try {
                Files.deleteIfExists(update.packageFile());
                restartAfterUpdate(update.notesFile(), tag);
                Platform.exit();
            } catch (IOException exception) {
                writeFailureLog(update.packageFile(), exception, "restart");
                UiUtil.error(owner, "Update Installed",
                        "The update installed successfully, but PinDB could not restart automatically. "
                                + "Open PinDB from the application menu.", exception);
            }
        });
        task.setOnFailed(event -> {
            installing.set(false);
            dialog.close();
            Throwable failure = task.getException();
            Path log = writeFailureLog(update.packageFile(), failure, "install");
            showFailureAlert(owner, update.packageFile(), update.releasePackage().type(),
                    update.distribution(), failure, log);
        });
        Thread thread = new Thread(task, "pindb-update-install");
        thread.setDaemon(true);
        thread.start();
    }

    private static boolean installationSupported(Window owner, LinuxDistribution distribution,
                                                  ReleasePackage releasePackage) {
        if (!distribution.isLinux()) {
            UiUtil.warning(owner, "Automatic Update Unavailable",
                    "Automatic package installation is available only on supported Linux distributions.");
            return false;
        }
        Optional<LinuxPackageType> expected = distribution.packageType();
        if (expected.isEmpty()) {
            UiUtil.warning(owner, "Automatic Update Unavailable",
                    "Automatic installation currently supports Debian-family and Fedora-family Linux systems.");
            return false;
        }
        if (expected.get() != releasePackage.type()) {
            UiUtil.warning(owner, "Automatic Update Unavailable",
                    "The release does not contain the correct package type for " + distribution.prettyName() + ".");
            return false;
        }
        if (distribution.immutable()) {
            UiUtil.warning(owner, "Automatic Update Unavailable",
                    "Fedora Atomic desktops must install the RPM with rpm-ostree and reboot into the new deployment.");
            return false;
        }
        return true;
    }

    private static String safeAssetName(ReleasePackage releasePackage) {
        String name;
        try {
            name = Path.of(releasePackage.fileName()).getFileName().toString();
        } catch (RuntimeException exception) {
            name = "";
        }
        return name.isBlank() || !releasePackage.type().matchesFileName(name)
                ? "pindb-update" + releasePackage.type().extension() : name;
    }

    private void download(URI uri, Path destination, AtomicBoolean cancelled,
                          ProgressReporter progress, MessageReporter message) throws Exception {
        message.report("Downloading update package…");
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(10))
                .header("User-Agent", "PinDB-Updater").GET().build();
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
                progress.report(received, total);
            }
        } catch (Exception exception) {
            Files.deleteIfExists(destination);
            throw exception;
        }
    }

    private void verifyChecksum(Path packageFile, URI checksumUri, MessageReporter message) throws Exception {
        if (checksumUri == null) {
            return;
        }
        HttpRequest request = HttpRequest.newBuilder(checksumUri).timeout(Duration.ofSeconds(30))
                .header("User-Agent", "PinDB-Updater").GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Could not download the update checksum.");
        }
        Optional<String> expected = parseExpectedChecksum(response.body(), packageFile.getFileName().toString());
        if (expected.isPresent()) {
            message.report("Verifying downloaded package…");
            if (!sha256(packageFile).equalsIgnoreCase(expected.get())) {
                throw new IOException("The downloaded update failed its SHA-256 verification.");
            }
        }
    }

    static Optional<String> parseExpectedChecksum(String checksumText, String packageName) throws IOException {
        String expectedName = normalizeChecksumName(packageName);
        List<String> hashes = new ArrayList<>();
        for (String line : (checksumText == null ? "" : checksumText).lines().toList()) {
            String[] pieces = line.trim().split("\\s+", 2);
            if (pieces.length == 0 || !pieces[0].matches("(?i)[0-9a-f]{64}")) {
                continue;
            }
            hashes.add(pieces[0]);
            if (pieces.length == 1 || normalizeChecksumName(pieces[1]).equals(expectedName)) {
                return Optional.of(pieces[0]);
            }
        }
        if (hashes.size() == 1) {
            return Optional.of(hashes.getFirst());
        }
        throw new IOException("The checksum file did not contain an entry for " + packageName + ".");
    }

    private static String normalizeChecksumName(String value) {
        String name = value == null ? "" : value.trim();
        if (name.startsWith("*")) {
            name = name.substring(1).trim();
        }
        name = name.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        return (slash >= 0 ? name.substring(slash + 1) : name)
                .replace('~', '.').toLowerCase(Locale.ROOT);
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

    private static void installPrivileged(Path packageFile, LinuxPackageType type,
                                          MessageReporter message) throws Exception {
        if (installedLauncher() == null) {
            throw new IOException("Automatic installation is available after PinDB is installed from a native package.");
        }
        Path pkexec = Path.of("/usr/bin/pkexec");
        if (!Files.isExecutable(pkexec)) {
            throw new IOException("The pkexec administrator tool is not installed at /usr/bin/pkexec.");
        }
        Path manager = packageManagerCandidates(type).stream().filter(Files::isExecutable).findFirst()
                .orElseThrow(() -> new IOException(type == LinuxPackageType.DEB
                        ? "The apt-get package installer is unavailable."
                        : "Neither dnf5 nor dnf is available under /usr/bin."));
        Path script = packageFile.getParent().resolve("install-pindb-update-root.sh");
        Files.writeString(script, rootInstallScript(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        try {
            message.report("Approve the administrator prompt to install the update…");
            Process process = new ProcessBuilder(pkexec.toString(), "/bin/sh", script.toString(),
                    packageFile.toAbsolutePath().toString(), manager.toString(), type.scriptValue())
                    .redirectErrorStream(true).start();
            String output;
            try (InputStream input = process.getInputStream()) {
                output = new String(input.readAllBytes(), StandardCharsets.UTF_8).trim();
            }
            int status = process.waitFor();
            if (status != 0) {
                throw new IOException("The administrator installer exited with status " + status + ".\n\n"
                        + (output.isBlank() ? "No additional installer output was provided." : output));
            }
        } finally {
            Files.deleteIfExists(script);
        }
    }

    private static String rootInstallScript() {
        return """
                #!/bin/sh
                set -u
                PACKAGE="$1"
                MANAGER="$2"
                KIND="$3"
                BACKUP="/opt/pindb-update-backup-$$"
                HAD_OLD=0
                if [ -d /opt/pindb ]; then
                  cp -a /opt/pindb "$BACKUP" || exit 30
                  HAD_OLD=1
                fi
                if [ "$KIND" = "deb" ]; then
                  DEBIAN_FRONTEND=noninteractive "$MANAGER" install -y "$PACKAGE"
                else
                  "$MANAGER" install -y "$PACKAGE"
                fi
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
    }

    static List<Path> packageManagerCandidates(LinuxPackageType type) {
        return type == LinuxPackageType.DEB
                ? List.of(Path.of("/usr/bin/apt-get"))
                : List.of(Path.of("/usr/bin/dnf5"), Path.of("/usr/bin/dnf"));
    }

    static List<Path> installedLauncherCandidates() {
        return List.of(Path.of("/opt/pindb/pindb/bin/PinDB"), Path.of("/opt/pindb/bin/PinDB"),
                Path.of("/usr/local/bin/pindb"), Path.of("/usr/bin/pindb"));
    }

    private static Path installedLauncher() {
        return installedLauncherCandidates().stream().filter(Files::isExecutable).findFirst().orElse(null);
    }

    private static void restartAfterUpdate(Path notes, String tag) throws IOException {
        Path launcher = installedLauncher();
        if (launcher == null) {
            throw new IOException("The installed PinDB launcher could not be found after the update.");
        }
        new ProcessBuilder(launcher.toString(), "--updated-tag=" + tag, "--updated-notes=" + notes).start();
    }

    static String manualInstallCommand(Path packageFile, LinuxPackageType type,
                                       LinuxDistribution distribution) {
        String command = distribution.manualInstallCommand(packageFile);
        if (!command.isBlank()) {
            return command;
        }
        String quoted = "\"" + packageFile.toAbsolutePath().normalize() + "\"";
        return type == LinuxPackageType.DEB ? "sudo apt install " + quoted : "sudo dnf install " + quoted;
    }

    private void showFailureAlert(Window owner, Path packageFile, LinuxPackageType type,
                                  LinuxDistribution distribution, Throwable failure, Path log) {
        StringBuilder text = new StringBuilder("The current PinDB installation was preserved.\n\nCause: ")
                .append(failureSummary(failure));
        if (packageFile != null) {
            text.append("\n\nDownloaded package:\n").append(packageFile)
                    .append("\n\nManual command:\n")
                    .append(manualInstallCommand(packageFile, type, distribution));
        }
        if (log != null) {
            text.append("\n\nDiagnostic log:\n").append(log);
        }
        Alert alert = new Alert(Alert.AlertType.ERROR);
        if (owner != null && owner.isShowing()) {
            alert.initOwner(owner);
        }
        alert.setTitle("Update Failed");
        alert.setHeaderText("PinDB could not install the update");
        alert.setContentText(text.toString());
        alert.getDialogPane().setPrefWidth(720);
        alert.getDialogPane().sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene != null) {
                UiUtil.applyStyles(newScene, settings);
            }
        });
        alert.showAndWait();
    }

    private static Path writeFailureLog(Path packageFile, Throwable failure, String stage) {
        try {
            Path log = AppPaths.ensure(AppPaths.stateDirectory()).resolve("update-error.log");
            StringWriter trace = new StringWriter();
            if (failure != null) {
                failure.printStackTrace(new PrintWriter(trace));
            }
            Files.writeString(log, "PinDB update failure\nTime: " + Instant.now() + "\nStage: " + stage
                            + "\nPackage: " + (packageFile == null ? "none" : packageFile)
                            + "\nCause: " + failureSummary(failure) + "\n\n" + trace,
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return log;
        } catch (RuntimeException | IOException exception) {
            exception.printStackTrace(System.err);
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
        return root.getMessage() == null || root.getMessage().isBlank()
                ? root.getClass().getSimpleName() : root.getMessage();
    }

    public static void showFailedInstallPrompt(Window owner, Path packageFile) {
        LinuxPackageType type = packageFile.toString().toLowerCase(Locale.ROOT).endsWith(".rpm")
                ? LinuxPackageType.RPM : LinuxPackageType.DEB;
        LinuxDistribution distribution = LinuxDistribution.current();
        Alert alert = new Alert(Alert.AlertType.ERROR);
        if (owner != null && owner.isShowing()) {
            alert.initOwner(owner);
        }
        alert.setTitle("PinDB Update Failed");
        alert.setHeaderText("The update could not be installed");
        alert.setContentText("Your previous PinDB installation was preserved. The downloaded package is at:\n"
                + packageFile + "\n\nInstall it manually with:\n"
                + manualInstallCommand(packageFile, type, distribution));
        alert.getButtonTypes().setAll(ButtonType.CLOSE);
        alert.showAndWait();
    }

    private record DownloadedUpdate(Path packageFile, Path notesFile,
                                    ReleasePackage releasePackage, LinuxDistribution distribution) {
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
