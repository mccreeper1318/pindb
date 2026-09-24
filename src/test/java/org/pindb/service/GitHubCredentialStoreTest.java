package org.pindb.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GitHubCredentialStoreTest {
    @TempDir
    Path tempDirectory;

    @Test
    void fallbackCredentialFileIsOwnerOnly() throws Exception {
        Path destination = tempDirectory.resolve("config").resolve("github-authorization.json");

        GitHubCredentialStore.saveToFallbackFile(destination, "{\"accessToken\":\"secret\"}");

        assertOwnerOnly(destination);
        assertEquals("{\"accessToken\":\"secret\"}", Files.readString(destination));
    }

    @Test
    void temporaryCredentialFileStartsOwnerOnly() throws Exception {
        Path parent = tempDirectory.resolve("config");
        Files.createDirectories(parent);

        Path temporaryPath;
        Path stagingDirectory;
        try (GitHubCredentialStore.SecureTemporaryFile temporary =
                     GitHubCredentialStore.createSecureTemporaryFile(parent)) {
            temporaryPath = temporary.path();
            stagingDirectory = temporary.directory();
            assertOwnerOnly(temporaryPath);
            if (isWindows()) {
                assertNotNull(stagingDirectory);
                assertOwnerOnly(stagingDirectory);
            }
        }

        assertFalse(Files.exists(temporaryPath));
        if (isWindows()) {
            assertFalse(Files.exists(stagingDirectory));
        }
    }

    private static void assertOwnerOnly(Path path) throws IOException {
        if (isWindows()) {
            AclFileAttributeView view = Files.getFileAttributeView(path, AclFileAttributeView.class);
            assertNotNull(view);
            assertFalse(view.getAcl().isEmpty());
            for (AclEntry entry : view.getAcl()) {
                assertEquals(Files.getOwner(path), entry.principal());
            }
        } else {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path);
            assertEquals(EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), permissions);
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
