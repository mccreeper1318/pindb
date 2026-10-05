package org.pindb.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.pindb.platform.LinuxDistribution;
import org.pindb.platform.NativePackageType;
import org.pindb.platform.OperatingSystem;
import org.pindb.platform.SystemArchitecture;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MacPlatformRegressionTest {
    private static final String HASH = "a8db1322913165890e748951593dc7ecb8e2a544cf34b696388a155cfa78e403";

    @Test
    void arm64MacSelectsOnlyArm64PkgWithMatchingChecksum() {
        List<Map<String, Object>> assets = List.of(
                asset("PinDB-0.3-beta.5-macos-x64.pkg"),
                asset("PinDB-0.3-beta.5-macos-x64.pkg.sha256"),
                asset("PinDB-0.3-beta.5-macos-arm64.pkg"),
                asset("PinDB-0.3-beta.5-macos-arm64.pkg.sha256"));

        ReleasePackage selected = UpdateService.selectPackage(
                assets, NativePackageType.MACOS_PKG, SystemArchitecture.AARCH64).orElseThrow();

        assertEquals("PinDB-0.3-beta.5-macos-arm64.pkg", selected.fileName());
        assertEquals(SystemArchitecture.AARCH64, selected.architecture());
        assertTrue(selected.checksumUri().toString().endsWith("PinDB-0.3-beta.5-macos-arm64.pkg.sha256"));
    }

    @Test
    void intelMacSelectsOnlyX64PkgWithMatchingChecksum() {
        List<Map<String, Object>> assets = List.of(
                asset("PinDB-0.3-beta.5-macos-arm64.pkg"),
                asset("PinDB-0.3-beta.5-macos-arm64.pkg.sha256"),
                asset("PinDB-0.3-beta.5-macos-x64.pkg"),
                asset("PinDB-0.3-beta.5-macos-x64.pkg.sha256"));

        ReleasePackage selected = UpdateService.selectPackage(
                assets, NativePackageType.MACOS_PKG, SystemArchitecture.X86_64).orElseThrow();

        assertEquals("PinDB-0.3-beta.5-macos-x64.pkg", selected.fileName());
        assertEquals(SystemArchitecture.X86_64, selected.architecture());
        assertTrue(selected.checksumUri().toString().endsWith("PinDB-0.3-beta.5-macos-x64.pkg.sha256"));
    }

    @Test
    void macRejectsWrongAndUnknownArchitecturesWithoutFallback() {
        List<Map<String, Object>> armOnly = List.of(asset("PinDB-0.3-macos-arm64.pkg"));
        List<Map<String, Object>> both = List.of(
                asset("PinDB-0.3-macos-arm64.pkg"),
                asset("PinDB-0.3-macos-x64.pkg"));

        assertTrue(UpdateService.selectPackage(
                armOnly, NativePackageType.MACOS_PKG, SystemArchitecture.X86_64).isEmpty());
        assertTrue(UpdateService.selectPackage(
                both, NativePackageType.MACOS_PKG, SystemArchitecture.UNKNOWN).isEmpty());
        assertTrue(UpdateService.selectPackage(
                List.of(asset("PinDB-0.3-macos.pkg")),
                NativePackageType.MACOS_PKG, SystemArchitecture.AARCH64).isEmpty());
    }

    @Test
    void macPkgWithoutPublishedChecksumRemainsUnverified() {
        ReleasePackage selected = UpdateService.selectPackage(
                List.of(asset("PinDB-0.3-macos-arm64.pkg")),
                NativePackageType.MACOS_PKG,
                SystemArchitecture.AARCH64).orElseThrow();

        assertNull(selected.checksumUri());
    }

    @Test
    void macPkgChecksumParserRequiresUsableDigestForTargetPackage() throws IOException {
        String checksum = HASH + "  PinDB-0.3-beta.5-macos-arm64.pkg\n";
        assertEquals(HASH, UpdateInstaller.parseExpectedChecksum(
                checksum, "PinDB-0.3-beta.5-macos-arm64.pkg").orElseThrow());

        String ambiguous = HASH + "  PinDB-0.3-beta.5-macos-x64.pkg\n"
                + "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
                + "  PinDB-0.3-beta.5-other.pkg\n";
        assertThrows(IOException.class, () -> UpdateInstaller.parseExpectedChecksum(
                ambiguous, "PinDB-0.3-beta.5-macos-arm64.pkg"));
    }

    @Test
    @EnabledOnOs(OS.MAC)
    void macManualRecoveryUsesNativeInstallerOpenCommand() {
        LinuxDistribution mac = LinuxDistribution.detect("Mac OS X", "");
        Path staged = Path.of(
                "/private/var/tmp/PinDB-verified-update.Ab12Cd34/PinDB-verified-update.pkg");

        assertEquals(
                "/usr/bin/open \"" + staged + "\"",
                UpdateInstaller.manualInstallCommand(staged, NativePackageType.MACOS_PKG, mac));
        assertEquals(List.of("/usr/bin/open", staged.toString()),
                UpdateInstaller.macInstallerCommand(staged));
    }

    @Test
    void macPkgCannotEnterLinuxPrivilegedUpdatePath() {
        assertTrue(UpdateInstaller.packageManagerCandidates(NativePackageType.MACOS_PKG).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> UpdateInstaller.privilegedInstallCommand(
                Path.of("/usr/bin/pkexec"),
                Path.of("/opt/pindb/pindb/bin/pindb-update-helper"),
                Path.of("/tmp/PinDB-0.3-macos-arm64.pkg"),
                HASH,
                NativePackageType.MACOS_PKG));
    }

    @Test
    void windowsAndLinuxPackageSelectionRemainUnchanged() {
        LinuxDistribution windows = LinuxDistribution.detect("Windows 11", "");
        LinuxDistribution debian = LinuxDistribution.detect("Linux", "ID=debian\nID_LIKE=debian\n");
        LinuxDistribution fedora = LinuxDistribution.detect("Linux", "ID=fedora\nID_LIKE=\"rhel fedora\"\n");

        assertEquals(NativePackageType.WINDOWS_EXE,
                UpdateService.packageTypeFor(OperatingSystem.WINDOWS, windows).orElseThrow());
        assertEquals(NativePackageType.DEB,
                UpdateService.packageTypeFor(OperatingSystem.LINUX, debian).orElseThrow());
        assertEquals(NativePackageType.RPM,
                UpdateService.packageTypeFor(OperatingSystem.LINUX, fedora).orElseThrow());
    }

    private static Map<String, Object> asset(String name) {
        return Map.of(
                "name", name,
                "browser_download_url", "https://example.invalid/" + name);
    }
}
