package org.pindb.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.pindb.platform.LinuxDistribution;
import org.pindb.platform.NativePackageType;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void acceptsMacPkgChecksum() throws IOException {
        String checksum = HASH + "  PinDB-0.3-beta.1-macos-arm64.pkg\n";
        assertEquals(HASH, UpdateInstaller.parseExpectedChecksum(
                checksum, "PinDB-0.3-beta.1-macos-arm64.pkg").orElseThrow());
    }

    @Test
    void buildsMacManualInstallCommand() {
        LinuxDistribution mac = LinuxDistribution.detect("Mac OS X", "");
        assertEquals("/usr/bin/open \"/tmp/PinDB-0.3-macos-arm64.pkg\"",
                UpdateInstaller.manualInstallCommand(
                        Path.of("/tmp/PinDB-0.3-macos-arm64.pkg"), NativePackageType.MACOS_PKG, mac));
    }

    @Test
    void buildsMacInstallerCommandWithoutShellQuoting() {
        assertEquals(List.of(
                        "/usr/bin/open",
                        "/tmp/PinDB update/PinDB-0.3-beta.1-macos-arm64.pkg"),
                UpdateInstaller.macInstallerCommand(
                        Path.of("/tmp/PinDB update/PinDB-0.3-beta.1-macos-arm64.pkg")));
    }

    @Test
    void macPackagesHaveNoLinuxPackageManagerCandidates() {
        assertTrue(UpdateInstaller.packageManagerCandidates(NativePackageType.MACOS_PKG).isEmpty());
    }

    @Test
    void macPackagesCannotBuildPrivilegedLinuxInstallCommands() {
        assertThrows(IllegalArgumentException.class, () -> UpdateInstaller.privilegedInstallCommand(
                Path.of("/usr/bin/pkexec"),
                Path.of("/opt/pindb/pindb/bin/pindb-update-helper"),
                Path.of("/tmp/PinDB-0.3-macos-arm64.pkg"), HASH, NativePackageType.MACOS_PKG));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void includesActualJpackageLauncherLocation() {
        assertTrue(UpdateInstaller.installedLauncherCandidates().stream()
                .anyMatch(path -> path.toString().equals("/opt/pindb/pindb/bin/PinDB")));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void includesPackagedRootOwnedUpdateHelperLocation() {
        assertTrue(UpdateInstaller.installedUpdateHelperCandidates().stream()
                .anyMatch(path -> path.toString().equals(
                        "/opt/pindb/pindb/bin/pindb-update-helper")));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void privilegedCommandExecutesOnlyPackagedHelper() {
        List<String> command = UpdateInstaller.privilegedInstallCommand(
                Path.of("/usr/bin/pkexec"),
                Path.of("/opt/pindb/pindb/bin/pindb-update-helper"),
                Path.of("/tmp/pindb.rpm"), HASH, NativePackageType.RPM);

        assertEquals(List.of(
                "/usr/bin/pkexec",
                "/opt/pindb/pindb/bin/pindb-update-helper",
                "install",
                "rpm",
                HASH,
                "/tmp/pindb.rpm"), command);
        assertTrue(command.stream().noneMatch(value -> value.equals("/bin/sh")));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void includesDnf5AndDnfCandidatesForRpmUpdates() {
        assertEquals(List.of(Path.of("/usr/bin/dnf5"), Path.of("/usr/bin/dnf")),
                UpdateInstaller.packageManagerCandidates(NativePackageType.RPM));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void buildsFedoraManualInstallCommand() {
        LinuxDistribution fedora = LinuxDistribution.detect("Linux", "ID=fedora\nID_LIKE=\"rhel fedora\"\n");
        assertTrue(UpdateInstaller.manualInstallCommand(
                Path.of("/tmp/pindb.rpm"), NativePackageType.RPM, fedora)
                .startsWith("sudo dnf install"));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void buildsRestartCommandWithoutLosingPrereleaseTagOrNotesPath() {
        assertEquals(List.of(
                        "/opt/pindb/pindb/bin/PinDB",
                        "--updated-tag=0.2.1-beta.2",
                        "--updated-notes=/tmp/PinDB update notes.md"),
                UpdateInstaller.restartCommand(
                        Path.of("/opt/pindb/pindb/bin/PinDB"),
                        Path.of("/tmp/PinDB update notes.md"),
                        "0.2.1-beta.2"));
    }
}
