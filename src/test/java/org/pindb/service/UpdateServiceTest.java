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
        List<Map<String, Object>> assets = List.of(
                asset("pindb_0.2-1_amd64.deb"),
                asset("pindb-0.2-1.x86_64.rpm"),
                asset("pindb-0.2-1.x86_64.rpm.sha256"));

        ReleasePackage selected = UpdateService.selectPackage(
                assets, LinuxPackageType.RPM, SystemArchitecture.X86_64).orElseThrow();

        assertEquals("pindb-0.2-1.x86_64.rpm", selected.fileName());
        assertEquals(LinuxPackageType.RPM, selected.type());
        assertEquals(SystemArchitecture.X86_64, selected.architecture());
        assertTrue(selected.checksumUri().toString().endsWith(".rpm.sha256"));
    }

    @Test
    void doesNotUsePackageForWrongArchitecture() {
        List<Map<String, Object>> assets = List.of(asset("pindb-0.2-1.aarch64.rpm"));
        assertTrue(UpdateService.selectPackage(
                assets, LinuxPackageType.RPM, SystemArchitecture.X86_64).isEmpty());
    }

    @Test
    void selectsDebianPackageForDebianFamily() {
        List<Map<String, Object>> assets = List.of(
                asset("pindb_0.2-1_amd64.deb"),
                asset("pindb-0.2-1.x86_64.rpm"));
        ReleasePackage selected = UpdateService.selectPackage(
                assets, LinuxPackageType.DEB, SystemArchitecture.X86_64).orElseThrow();
        assertEquals("pindb_0.2-1_amd64.deb", selected.fileName());
    }

    private static Map<String, Object> asset(String name) {
        return Map.of("name", name, "browser_download_url", "https://example.invalid/" + name);
    }
}
