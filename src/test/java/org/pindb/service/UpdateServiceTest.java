package org.pindb.service;

import org.junit.jupiter.api.Test;
import org.pindb.platform.LinuxPackageType;
import org.pindb.platform.SystemArchitecture;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateServiceTest {
    @Test
    void selectsFedoraRpmAndMatchingChecksum() {
        List<Map<String, Object>> assets = List.of(asset("pindb_0.2-1_amd64.deb"), asset("pindb-0.2-1.x86_64.rpm"), asset("pindb-0.2-1.x86_64.rpm.sha256"));
        ReleasePackage selected = UpdateService.selectPackage(assets, LinuxPackageType.RPM, SystemArchitecture.X86_64).orElseThrow();
        assertEquals("pindb-0.2-1.x86_64.rpm", selected.fileName());
        assertEquals(LinuxPackageType.RPM, selected.type());
        assertEquals(SystemArchitecture.X86_64, selected.architecture());
        assertTrue(selected.checksumUri().toString().endsWith(".rpm.sha256"));
    }

    @Test void doesNotUsePackageForWrongArchitecture() {
        assertTrue(UpdateService.selectPackage(List.of(asset("pindb-0.2-1.aarch64.rpm")), LinuxPackageType.RPM, SystemArchitecture.X86_64).isEmpty());
    }

    @Test void selectsDebianPackageForDebianFamily() {
        ReleasePackage selected = UpdateService.selectPackage(List.of(asset("pindb_0.2-1_amd64.deb"), asset("pindb-0.2-1.x86_64.rpm")), LinuxPackageType.DEB, SystemArchitecture.X86_64).orElseThrow();
        assertEquals("pindb_0.2-1_amd64.deb", selected.fileName());
    }

    @Test void selectsWindowsX64InstallerAndChecksum() {
        List<Map<String, Object>> assets = List.of(asset("PinDB-0.3-windows-x64.exe"), asset("PinDB-0.3-windows-x64.exe.sha256"), asset("pindb-0.3-1.x86_64.rpm"));
        ReleasePackage selected = UpdateService.selectPackage(assets, LinuxPackageType.WINDOWS_EXE, SystemArchitecture.X86_64).orElseThrow();
        assertEquals("PinDB-0.3-windows-x64.exe", selected.fileName());
        assertEquals(LinuxPackageType.WINDOWS_EXE, selected.type());
        assertEquals(SystemArchitecture.X86_64, selected.architecture());
        assertTrue(selected.checksumUri().toString().endsWith(".exe.sha256"));
    }

    @Test void recognizesWindowsPlatformNames() {
        assertTrue(UpdateService.isWindows("Windows 11"));
    }

    private static Map<String, Object> asset(String name) { return Map.of("name", name, "browser_download_url", "https://example.invalid/" + name); }
}
