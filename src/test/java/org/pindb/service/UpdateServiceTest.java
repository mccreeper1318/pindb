package org.pindb.service;

import org.junit.jupiter.api.Test;
import org.pindb.platform.LinuxDistribution;
import org.pindb.platform.NativePackageType;
import org.pindb.platform.OperatingSystem;
import org.pindb.platform.SystemArchitecture;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateServiceTest {
    @Test
    void selectsFedoraRpmAndMatchingChecksum() {
        List<Map<String, Object>> assets = List.of(asset("pindb_0.2-1_amd64.deb"), asset("pindb-0.2-1.x86_64.rpm"), asset("pindb-0.2-1.x86_64.rpm.sha256"));
        ReleasePackage selected = UpdateService.selectPackage(assets, NativePackageType.RPM, SystemArchitecture.X86_64).orElseThrow();
        assertEquals("pindb-0.2-1.x86_64.rpm", selected.fileName());
        assertEquals(NativePackageType.RPM, selected.type());
        assertEquals(SystemArchitecture.X86_64, selected.architecture());
        assertTrue(selected.checksumUri().toString().endsWith(".rpm.sha256"));
    }

    @Test void doesNotUsePackageForWrongArchitecture() {
        assertTrue(UpdateService.selectPackage(List.of(asset("pindb-0.2-1.aarch64.rpm")), NativePackageType.RPM, SystemArchitecture.X86_64).isEmpty());
    }

    @Test void selectsDebianPackageForDebianFamily() {
        ReleasePackage selected = UpdateService.selectPackage(List.of(asset("pindb_0.2-1_amd64.deb"), asset("pindb-0.2-1.x86_64.rpm")), NativePackageType.DEB, SystemArchitecture.X86_64).orElseThrow();
        assertEquals("pindb_0.2-1_amd64.deb", selected.fileName());
    }

    @Test void selectsWindowsX64InstallerAndChecksum() {
        List<Map<String, Object>> assets = List.of(asset("PinDB-0.3-windows-x64.exe"), asset("PinDB-0.3-windows-x64.exe.sha256"), asset("pindb-0.3-1.x86_64.rpm"));
        ReleasePackage selected = UpdateService.selectPackage(assets, NativePackageType.WINDOWS_EXE, SystemArchitecture.X86_64).orElseThrow();
        assertEquals("PinDB-0.3-windows-x64.exe", selected.fileName());
        assertEquals(NativePackageType.WINDOWS_EXE, selected.type());
        assertEquals(SystemArchitecture.X86_64, selected.architecture());
        assertTrue(selected.checksumUri().toString().endsWith(".exe.sha256"));
    }

    @Test void selectsMacArm64InstallerAndChecksum() {
        List<Map<String, Object>> assets = List.of(
                asset("PinDB-0.3-macos-x64.pkg"),
                asset("PinDB-0.3-macos-arm64.pkg"),
                asset("PinDB-0.3-macos-arm64.pkg.sha256"));
        ReleasePackage selected = UpdateService.selectPackage(assets, NativePackageType.MACOS_PKG, SystemArchitecture.AARCH64).orElseThrow();
        assertEquals("PinDB-0.3-macos-arm64.pkg", selected.fileName());
        assertEquals(NativePackageType.MACOS_PKG, selected.type());
        assertEquals(SystemArchitecture.AARCH64, selected.architecture());
        assertTrue(selected.checksumUri().toString().endsWith(".pkg.sha256"));
    }

    @Test void selectsMacX64InstallerAndPlatformChecksumFile() {
        List<Map<String, Object>> assets = List.of(
                asset("PinDB-0.3-macos-arm64.pkg"),
                asset("PinDB-0.3-macos-x64.pkg"),
                asset("checksums-macos.sha256"));
        ReleasePackage selected = UpdateService.selectPackage(assets, NativePackageType.MACOS_PKG, SystemArchitecture.X86_64).orElseThrow();
        assertEquals("PinDB-0.3-macos-x64.pkg", selected.fileName());
        assertEquals(SystemArchitecture.X86_64, selected.architecture());
        assertTrue(selected.checksumUri().toString().endsWith("checksums-macos.sha256"));
    }

    @Test void macArchitectureComesFromPackageSuffixNotVersionToken() {
        List<Map<String, Object>> assets = List.of(
                asset("PinDB-0.3-x64.1-macos-arm64.pkg"),
                asset("PinDB-0.3-arm64.1-macos-x64.pkg"));

        ReleasePackage arm = UpdateService.selectPackage(
                assets, NativePackageType.MACOS_PKG, SystemArchitecture.AARCH64).orElseThrow();
        ReleasePackage intel = UpdateService.selectPackage(
                assets, NativePackageType.MACOS_PKG, SystemArchitecture.X86_64).orElseThrow();

        assertEquals("PinDB-0.3-x64.1-macos-arm64.pkg", arm.fileName());
        assertEquals(SystemArchitecture.AARCH64, arm.architecture());
        assertEquals("PinDB-0.3-arm64.1-macos-x64.pkg", intel.fileName());
        assertEquals(SystemArchitecture.X86_64, intel.architecture());
    }

    @Test void macChecksumSelectionPrefersMacAggregateWhenOtherPlatformsComeFirst() {
        List<Map<String, Object>> assets = List.of(
                asset("PinDB-0.3-macos-arm64.pkg"),
                asset("checksums-linux.sha256"),
                asset("checksums-windows.sha256"),
                asset("checksums-macos.sha256"),
                asset("checksums.sha256"));
        ReleasePackage selected = UpdateService.selectPackage(assets, NativePackageType.MACOS_PKG, SystemArchitecture.AARCH64).orElseThrow();
        assertTrue(selected.checksumUri().toString().endsWith("checksums-macos.sha256"));
    }

    @Test void windowsChecksumSelectionPrefersWindowsAggregate() {
        List<Map<String, Object>> assets = List.of(
                asset("PinDB-0.3-windows-x64.exe"),
                asset("checksums-linux.sha256"),
                asset("checksums-macos.sha256"),
                asset("checksums-windows.sha256"),
                asset("checksums.sha256"));
        ReleasePackage selected = UpdateService.selectPackage(assets, NativePackageType.WINDOWS_EXE, SystemArchitecture.X86_64).orElseThrow();
        assertTrue(selected.checksumUri().toString().endsWith("checksums-windows.sha256"));
    }

    @Test void linuxChecksumSelectionPrefersLinuxAggregate() {
        List<Map<String, Object>> assets = List.of(
                asset("pindb-0.3-1.x86_64.rpm"),
                asset("checksums-windows.sha256"),
                asset("checksums-macos.sha256"),
                asset("checksums-linux.sha256"),
                asset("checksums.sha256"));
        ReleasePackage selected = UpdateService.selectPackage(assets, NativePackageType.RPM, SystemArchitecture.X86_64).orElseThrow();
        assertTrue(selected.checksumUri().toString().endsWith("checksums-linux.sha256"));
    }

    @Test void genericChecksumRemainsFallbackWhenPlatformAggregateIsAbsent() {
        List<Map<String, Object>> assets = List.of(
                asset("PinDB-0.3-macos-arm64.pkg"),
                asset("checksums-linux.sha256"),
                asset("checksums.sha256"));
        ReleasePackage selected = UpdateService.selectPackage(assets, NativePackageType.MACOS_PKG, SystemArchitecture.AARCH64).orElseThrow();
        assertTrue(selected.checksumUri().toString().endsWith("checksums.sha256"));
    }

    @Test void macUpdateSelectionRejectsWrongArchitecture() {
        assertTrue(UpdateService.selectPackage(
                List.of(asset("PinDB-0.3-macos-arm64.pkg")),
                NativePackageType.MACOS_PKG,
                SystemArchitecture.X86_64).isEmpty());
    }

    @Test void macUpdateSelectionRejectsUnknownHostArchitecture() {
        assertTrue(UpdateService.selectPackage(
                List.of(asset("PinDB-0.3-macos-arm64.pkg"), asset("PinDB-0.3-macos-x64.pkg")),
                NativePackageType.MACOS_PKG,
                SystemArchitecture.UNKNOWN).isEmpty());
    }

    @Test void macUpdateSelectionRejectsUnmarkedPackageArchitecture() {
        assertTrue(UpdateService.selectPackage(
                List.of(asset("PinDB-0.3-macos.pkg")),
                NativePackageType.MACOS_PKG,
                SystemArchitecture.AARCH64).isEmpty());
    }

    @Test void preservesCurrentWindowsPackageSelection() {
        LinuxDistribution nonLinux = LinuxDistribution.detect("Windows 11", "");
        assertEquals(NativePackageType.WINDOWS_EXE,
                UpdateService.packageTypeFor(OperatingSystem.WINDOWS, nonLinux).orElseThrow());
    }

    @Test void preservesCurrentLinuxPackageSelection() {
        LinuxDistribution ubuntu = LinuxDistribution.detect("Linux", "ID=ubuntu\nID_LIKE=debian\n");
        assertEquals(NativePackageType.DEB,
                UpdateService.packageTypeFor(OperatingSystem.LINUX, ubuntu).orElseThrow());
    }

    @Test void selectsMacPkgForMacUpdates() {
        LinuxDistribution nonLinux = LinuxDistribution.detect("Mac OS X", "");
        assertEquals(NativePackageType.MACOS_PKG,
                UpdateService.packageTypeFor(OperatingSystem.MACOS, nonLinux).orElseThrow());
    }

    private static Map<String, Object> asset(String name) { return Map.of("name", name, "browser_download_url", "https://example.invalid/" + name); }
}
