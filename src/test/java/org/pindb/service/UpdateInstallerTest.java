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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void macStagingAcceptsDistinctUniqueProtectedPaths() {
        Path first = Path.of("/private/var/tmp/PinDB-verified-update.Ab12Cd34/PinDB-verified-update.pkg");
        Path second = Path.of("/private/var/tmp/PinDB-verified-update.Z9y8X7w6/PinDB-verified-update.pkg");

        assertEquals(first, UpdateInstaller.parseMacStagedInstallerPath(first.toString()).orElseThrow());
        assertEquals(second, UpdateInstaller.parseMacStagedInstallerPath(second.toString()).orElseThrow());
        assertFalse(first.equals(second));
        assertTrue(UpdateInstaller.isExpectedMacStagedInstallerPath(first));
        assertTrue(UpdateInstaller.isExpectedMacStagedInstallerPath(second));
    }

    @Test
    void macStagingRejectsSharedOrMalformedProtectedPaths() {
        assertTrue(UpdateInstaller.parseMacStagedInstallerPath(
                "/private/var/tmp/PinDB-verified-update.pkg").isEmpty());
        assertTrue(UpdateInstaller.parseMacStagedInstallerPath(
                "/private/var/tmp/PinDB-verified-update.Ab12Cd34/not-the-installer.pkg").isEmpty());
        assertTrue(UpdateInstaller.parseMacStagedInstallerPath(
                "/tmp/PinDB-verified-update.Ab12Cd34/PinDB-verified-update.pkg").isEmpty());
        assertTrue(UpdateInstaller.parseMacStagedInstallerPath(
                "/private/var/tmp/PinDB-verified-update.bad_suffix/PinDB-verified-update.pkg").isEmpty());
        assertTrue(UpdateInstaller.parseMacStagedInstallerPath(
                "/private/var/tmp/PinDB-verified-update.Ab12Cd34/PinDB-verified-update.pkg\n"
                        + "/private/var/tmp/PinDB-verified-update.Z9y8X7w6/PinDB-verified-update.pkg").isEmpty());
    }

    @Test
    void macStagingCommandKeepsUntrustedValuesOutOfElevatedScript() {
        Path source = Path.of("/tmp/PinDB update/quote's package.pkg");
        List<String> command = UpdateInstaller.macStagingCommand(source, HASH.toUpperCase());

        assertEquals("/usr/bin/osascript", command.getFirst());
        assertEquals(source.toAbsolutePath().normalize().toString(), command.get(command.size() - 2));
        assertEquals(HASH, command.getLast());

        String appleScript = String.join("\n", command.subList(0, command.size() - 2));
        assertTrue(appleScript.contains("with administrator privileges"));
        assertTrue(appleScript.contains("/usr/bin/mktemp -d /private/var/tmp/PinDB-verified-update.XXXXXXXX"));
        assertTrue(appleScript.contains("/usr/bin/install -o root -g wheel -m 0400"));
        assertTrue(appleScript.contains("/usr/bin/shasum -a 256"));
        assertTrue(appleScript.contains("PinDB-verified-update.pkg"));
        assertTrue(appleScript.contains("/usr/bin/nohup /bin/sh -c"));
        assertTrue(appleScript.contains("pindb-cleanup 86400"));
        assertTrue(appleScript.contains(">/dev/null 2>&1 </dev/null &"));
        assertFalse(appleScript.contains("/private/var/tmp/PinDB-verified-update.pkg"));
        assertFalse(appleScript.contains(source.toString()));
        assertFalse(appleScript.contains(HASH));
    }

    @Test
    void macStagingRejectsInvalidDigestBeforeAuthorization() {
        assertThrows(IllegalArgumentException.class, () -> UpdateInstaller.macStagingCommand(
                Path.of("/tmp/PinDB.pkg"), "not-a-sha256"));
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
