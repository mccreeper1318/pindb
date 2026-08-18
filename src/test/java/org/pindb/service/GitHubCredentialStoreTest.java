package org.pindb.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

class GitHubCredentialStoreTest {
    @TempDir
    Path tempDirectory;

    @Test
    void fallbackCredentialFileIsOwnerOnly() throws Exception {
        Path destination = tempDirectory.resolve("config").resolve("github-authorization.json");

        GitHubCredentialStore.saveToFallbackFile(destination, "{\"accessToken\":\"secret\"}");

        if (isWindows()) {
            AclFileAttributeView view = Files.getFileAttributeView(destination, AclFileAttributeView.class);
            assertFalse(view.getAcl().isEmpty());
            for (AclEntry entry : view.getAcl()) {
                assertEquals(Files.getOwner(destination), entry.principal());
            }
        } else {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(destination);
            assertEquals(EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), permissions);
        }
        assertEquals("{\"accessToken\":\"secret\"}", Files.readString(destination));
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
