package org.pindb.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GitHubCredentialStoreTest {
    @TempDir
    Path tempDirectory;

    @Test
    void fallbackCredentialFileIsOwnerOnly() throws Exception {
        Path destination = tempDirectory.resolve("config").resolve("github-authorization.json");

        GitHubCredentialStore.saveToFallbackFile(destination, "{\"accessToken\":\"secret\"}");

        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(destination);
        assertEquals(EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), permissions);
        assertEquals("{\"accessToken\":\"secret\"}", Files.readString(destination));
    }
}
