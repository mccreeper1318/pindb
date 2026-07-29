package org.pindb.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateInstallerTest {
    private static final String HASH = "a8db1322913165890e748951593dc7ecb8e2a544cf34b696388a155cfa78e403";

    @Test
    void acceptsGitHubNormalizedPrereleaseFilename() throws IOException {
        String checksum = HASH + "  pindb_0.1.1-0~beta.6_amd64.deb\n";

        assertEquals(HASH, UpdateInstaller.parseExpectedChecksum(
                checksum, "pindb_0.1.1-0.beta.6_amd64.deb").orElseThrow());
    }

    @Test
    void acceptsChecksumFilenameWithDirectory() throws IOException {
        String checksum = HASH + "  build/packages/pindb_0.1.1-0.beta.6_amd64.deb\n";

        assertEquals(HASH, UpdateInstaller.parseExpectedChecksum(
                checksum, "pindb_0.1.1-0.beta.6_amd64.deb").orElseThrow());
    }

    @Test
    void acceptsSingleUnambiguousDigest() throws IOException {
        String checksum = HASH + "  differently-normalized-name.deb\n";

        assertEquals(HASH, UpdateInstaller.parseExpectedChecksum(
                checksum, "pindb_0.1.1-0.beta.6_amd64.deb").orElseThrow());
    }

    @Test
    void includesActualJpackageLauncherLocation() {
        assertTrue(UpdateInstaller.installedLauncherCandidates().stream()
                .anyMatch(path -> path.toString().equals("/opt/pindb/pindb/bin/PinDB")));
    }
}
