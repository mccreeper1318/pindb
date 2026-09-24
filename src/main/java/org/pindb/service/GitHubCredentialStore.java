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
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryFlag;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
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
            saveToFallbackFile(FALLBACK_FILE, json);
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
            secureOwnerOnly(FALLBACK_FILE);
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

    static void saveToFallbackFile(Path destination, String json) throws IOException {
        Path parent = destination.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        SecureTemporaryFile temporary;
        try {
            temporary = createSecureTemporaryFile(parent);
        } catch (UnsupportedOperationException exception) {
            throw new IOException("This filesystem cannot create the GitHub credential fallback with owner-only permissions.",
                    exception);
        }

        try (temporary) {
            Path temporaryPath = temporary.path();
            Files.writeString(temporaryPath, json, StandardCharsets.UTF_8,
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            secureOwnerOnly(temporaryPath);
            try {
                Files.move(temporaryPath, destination,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporaryPath, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            secureOwnerOnly(destination);
        }
    }

    static SecureTemporaryFile createSecureTemporaryFile(Path parent) throws IOException {
        if (!isWindows()) {
            Path temporary = Files.createTempFile(parent, ".github-authorization-", ".tmp",
                    PosixFilePermissions.asFileAttribute(OWNER_ONLY_PERMISSIONS));
            return new SecureTemporaryFile(temporary, null);
        }

        Path secureDirectory = Files.createTempDirectory(parent, ".github-authorization-");
        try {
            disableWindowsAclInheritance(secureDirectory);
            UserPrincipal owner = Files.getOwner(secureDirectory);
            AclFileAttributeView directoryView = Files.getFileAttributeView(secureDirectory, AclFileAttributeView.class);
            if (directoryView == null) {
                throw new IOException("The filesystem does not expose Windows ACLs for the GitHub credential staging directory.");
            }
            directoryView.setAcl(ownerOnlyAcl(owner, true));

            List<AclEntry> acl = ownerOnlyAcl(owner, false);
            FileAttribute<List<AclEntry>> aclAttribute = new FileAttribute<>() {
                @Override
                public String name() {
                    return "acl:acl";
                }

                @Override
                public List<AclEntry> value() {
                    return acl;
                }
            };
            Path temporary = Files.createTempFile(secureDirectory, "credential-", ".tmp", aclAttribute);
            return new SecureTemporaryFile(temporary, secureDirectory);
        } catch (IOException | RuntimeException exception) {
            try {
                Files.deleteIfExists(secureDirectory);
            } catch (IOException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw exception;
        }
    }

    private static void disableWindowsAclInheritance(Path directory) throws IOException {
        Process process;
        try {
            process = new ProcessBuilder("icacls.exe", directory.toString(), "/inheritance:r")
                    .redirectErrorStream(true)
                    .start();
        } catch (IOException exception) {
            throw new IOException("Could not start Windows ACL protection for the GitHub credential staging directory.",
                    exception);
        }

        try {
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("Could not disable inherited Windows ACLs for the GitHub credential staging directory"
                        + (output.isBlank() ? "." : ": " + output));
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while protecting the GitHub credential staging directory.", exception);
        }
    }

    private static void secureOwnerOnly(Path path) throws IOException {
        if (isWindows()) {
            AclFileAttributeView view = Files.getFileAttributeView(path, AclFileAttributeView.class);
            if (view == null) {
                throw new IOException("The filesystem does not expose Windows ACLs for the GitHub credential fallback.");
            }
            view.setAcl(ownerOnlyAcl(Files.getOwner(path), false));
            return;
        }
        Files.setPosixFilePermissions(path, OWNER_ONLY_PERMISSIONS);
    }

    private static List<AclEntry> ownerOnlyAcl(UserPrincipal owner, boolean inheritable) {
        AclEntry.Builder builder = AclEntry.newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(owner)
                .setPermissions(EnumSet.allOf(AclEntryPermission.class));
        if (inheritable) {
            builder.setFlags(AclEntryFlag.FILE_INHERIT, AclEntryFlag.DIRECTORY_INHERIT);
        }
        return List.of(builder.build());
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static long longValue(Object value) {
        try {
            return Long.parseLong(MiniJson.string(value));
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    record SecureTemporaryFile(Path path, Path directory) implements AutoCloseable {
        @Override
        public void close() throws IOException {
            IOException failure = null;
            try {
                Files.deleteIfExists(path);
            } catch (IOException exception) {
                failure = exception;
            }
            if (directory != null) {
                try {
                    Files.deleteIfExists(directory);
                } catch (IOException exception) {
                    if (failure == null) {
                        failure = exception;
                    } else {
                        failure.addSuppressed(exception);
                    }
                }
            }
            if (failure != null) {
                throw failure;
            }
        }
    }
}
