package org.pindb.service;

import org.pindb.util.AppPaths;
import org.pindb.util.MiniJson;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class GitHubCredentialStore {
    private static final String SECRET_TOOL = "/usr/bin/secret-tool";
    private static final Path FALLBACK_FILE = AppPaths.configDirectory().resolve("github-authorization.json");

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
            Files.writeString(FALLBACK_FILE, json, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            secureFallbackFile();
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
            } catch (IOException | InterruptedException ignored) {
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
        } catch (IOException | InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private String loadFromFile() {
        try {
            return Files.isRegularFile(FALLBACK_FILE)
                    ? Files.readString(FALLBACK_FILE, StandardCharsets.UTF_8)
                    : "";
        } catch (IOException exception) {
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
            process.getOutputStream().write(value.getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().close();
            return process.waitFor() == 0;
        } catch (IOException | InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static long longValue(Object value) {
        try {
            return Long.parseLong(MiniJson.string(value));
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private static void secureFallbackFile() {
        try {
            Set<PosixFilePermission> permissions = EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(FALLBACK_FILE, permissions);
        } catch (UnsupportedOperationException | IOException ignored) {
            // Some file systems do not expose POSIX permissions.
        }
    }
}
