package org.pindb.service;

import org.junit.jupiter.api.Test;
import org.pindb.platform.LinuxDistribution;
import org.pindb.platform.LinuxPackageType;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

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
    void acceptsRpmChecksum() throws IOException {
        String checksum = HASH + "  pindb-0.2-0.beta.3.x86_64.rpm\n";
        assertEquals(HASH, UpdateInstaller.parseExpectedChecksum(
                checksum, "pindb-0.2-0.beta.3.x86_64.rpm").orElseThrow());
    }

    @Test
    void includesActualJpackageLauncherLocation() {
        assertTrue(UpdateInstaller.installedLauncherCandidates().stream()
                .anyMatch(path -> path.toString().equals("/opt/pindb/pindb/bin/PinDB")));
    }

    @Test
    void includesDnf5AndDnfCandidatesForRpmUpdates() {
        assertEquals(List.of(Path.of("/usr/bin/dnf5"), Path.of("/usr/bin/dnf")),
                UpdateInstaller.packageManagerCandidates(LinuxPackageType.RPM));
    }

    @Test
    void buildsFedoraManualInstallCommand() {
        LinuxDistribution fedora = LinuxDistribution.detect("Linux", "ID=fedora\nID_LIKE=\"rhel fedora\"\n");
        assertTrue(UpdateInstaller.manualInstallCommand(
                Path.of("/tmp/pindb.rpm"), LinuxPackageType.RPM, fedora)
                .startsWith("sudo dnf install"));
    }
}
