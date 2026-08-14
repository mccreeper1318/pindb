package org.pindb.service;

import org.pindb.util.AppPaths;
import org.pindb.util.MiniJson;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class GitHubCredentialStore {
    private static final String SECRET_TOOL = "/usr/bin/secret-tool";
    private static final Path FALLBACK_FILE = AppPaths.configDirectory().resolve("github-authorization.json");
    private static final Set<PosixFilePermission> OWNER_ONLY_PERMISSIONS = EnumSet.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE);

    Optional<GitHubAuthService.Token> load() {
        String json = loadFromKeyring().orElseGet(this::loadFromFile);
        if (json.isBlank()) {
            return Optional.empty();
        }
        try {
            Map<String, Object> value = MiniJson.object(MiniJson.parse(json));
            return Optional.of(new GitHubAuthService.Token(
                    MiniJson.string(value.get("accessToken")),
                    MiniJson.string(value.get("refreshToken")),
                    Instant.ofEpochSecond(longValue(value.get("expiresAt"))),
                    Instant.ofEpochSecond(longValue(value.get("refreshExpiresAt")))
            ));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    void save(GitHubAuthService.Token token) throws IOException {
        String json = MiniJson.stringify(Map.of(
                "accessToken", token.accessToken(),
                "refreshToken", token.refreshToken(),
                "expiresAt", token.expiresAt().getEpochSecond(),
                "refreshExpiresAt", token.refreshExpiresAt().getEpochSecond()
        ));
        if (!saveToKeyring(json)) {
            saveToFallbackFile(json);
        }
    }

    void clear() {
        try {
            Files.deleteIfExists(FALLBACK_FILE);
        } catch (IOException ignored) {
            // Clearing a stale fallback is best effort.
        }
        if (Files.isExecutable(Path.of(SECRET_TOOL))) {
            try {
                new ProcessBuilder(SECRET_TOOL, "clear", "application", "pindb", "account", "github")
                        .start().waitFor();
            } catch (IOException ignored) {
                // A broken keyring must not poison the worker thread or block the file fallback.
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private Optional<String> loadFromKeyring() {
        if (!Files.isExecutable(Path.of(SECRET_TOOL))) {
            return Optional.empty();
        }
        try {
            Process process = new ProcessBuilder(SECRET_TOOL, "lookup",
                    "application", "pindb", "account", "github")
                    .redirectErrorStream(true).start();
            String value = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return process.waitFor() == 0 && !value.isBlank() ? Optional.of(value) : Optional.empty();
        } catch (IOException exception) {
            return Optional.empty();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private String loadFromFile() {
        if (!Files.isRegularFile(FALLBACK_FILE)) {
            return "";
        }
        try {
            // Harden fallback files created by older PinDB versions before reading token data.
            Files.setPosixFilePermissions(FALLBACK_FILE, OWNER_ONLY_PERMISSIONS);
            return Files.readString(FALLBACK_FILE, StandardCharsets.UTF_8);
        } catch (UnsupportedOperationException | IOException exception) {
            // If owner-only permissions cannot be guaranteed, do not read credentials from this fallback.
            return "";
        }
    }

    private boolean saveToKeyring(String value) {
        if (!Files.isExecutable(Path.of(SECRET_TOOL))) {
            return false;
        }
        try {
            Process process = new ProcessBuilder(SECRET_TOOL, "store",
                    "--label=PinDB GitHub authorization",
                    "application", "pindb", "account", "github")
                    .redirectErrorStream(true).start();
            try (OutputStream output = process.getOutputStream()) {
                output.write(value.getBytes(StandardCharsets.UTF_8));
            }
            return process.waitFor() == 0;
        } catch (IOException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static void saveToFallbackFile(String json) throws IOException {
        Path parent = FALLBACK_FILE.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path temporary;
        try {
            temporary = Files.createTempFile(parent, ".github-authorization-", ".tmp",
                    PosixFilePermissions.asFileAttribute(OWNER_ONLY_PERMISSIONS));
        } catch (UnsupportedOperationException exception) {
            throw new IOException("This filesystem cannot create the GitHub credential fallback with owner-only permissions.",
                    exception);
        }

        try {
            Files.writeString(temporary, json, StandardCharsets.UTF_8,
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.setPosixFilePermissions(temporary, OWNER_ONLY_PERMISSIONS);
            try {
                Files.move(temporary, FALLBACK_FILE,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, FALLBACK_FILE, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.setPosixFilePermissions(FALLBACK_FILE, OWNER_ONLY_PERMISSIONS);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static long longValue(Object value) {
        try {
            return Long.parseLong(MiniJson.string(value));
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }
}
