package org.pindb.service;

import org.junit.jupiter.api.Test;
import org.pindb.platform.NativePackageType;
import org.pindb.platform.SystemArchitecture;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateChecksumSecurityTest {
    private static final String HASH = "a8db1322913165890e748951593dc7ecb8e2a544cf34b696388a155cfa78e403";
    private static final String PACKAGE = "PinDB-0.3.1-windows-x64.exe";

    @Test
    void automaticInstallSelectionRejectsPackageWithoutChecksumAsset() {
        assertTrue(UpdateService.selectInstallablePackage(
                List.of(asset(PACKAGE)),
                NativePackageType.WINDOWS_EXE,
                SystemArchitecture.X86_64).isEmpty());
    }

    @Test
    void automaticInstallSelectionAcceptsExactPackageChecksumAsset() {
        ReleasePackage selected = UpdateService.selectInstallablePackage(
                List.of(asset(PACKAGE), asset(PACKAGE + ".sha256")),
                NativePackageType.WINDOWS_EXE,
                SystemArchitecture.X86_64).orElseThrow();

        assertEquals(PACKAGE, selected.fileName());
        assertTrue(selected.checksumUri().toString().endsWith(PACKAGE + ".sha256"));
    }

    @Test
    void automaticInstallSelectionSkipsEarlierChecksumlessCandidate() {
        String unchecked = "PinDB-0.3.1-alt-windows-x64.exe";
        ReleasePackage selected = UpdateService.selectInstallablePackage(
                List.of(asset(unchecked), asset(PACKAGE), asset(PACKAGE + ".sha256")),
                NativePackageType.WINDOWS_EXE,
                SystemArchitecture.X86_64).orElseThrow();

        assertEquals(PACKAGE, selected.fileName());
        assertTrue(selected.checksumUri().toString().endsWith(PACKAGE + ".sha256"));
    }

    @Test
    void checksumAssetNameMustMatchPackageExactly() {
        assertTrue(UpdateService.selectInstallablePackage(
                List.of(asset(PACKAGE), asset(PACKAGE.toLowerCase() + ".sha256")),
                NativePackageType.WINDOWS_EXE,
                SystemArchitecture.X86_64).isEmpty());
    }

    @Test
    void checksumManifestRequiresExactPackageFilename() throws IOException {
        assertEquals(HASH, UpdateInstaller.parseExpectedChecksum(
                HASH + "  " + PACKAGE + "\n", PACKAGE).orElseThrow());
        assertEquals(HASH, UpdateInstaller.parseExpectedChecksum(
                HASH + " *" + PACKAGE + "\n", PACKAGE).orElseThrow());

        assertThrows(IOException.class, () -> UpdateInstaller.parseExpectedChecksum(HASH + "\n", PACKAGE));
        assertThrows(IOException.class, () -> UpdateInstaller.parseExpectedChecksum(
                HASH + "  " + PACKAGE.toLowerCase() + "\n", PACKAGE));
        assertThrows(IOException.class, () -> UpdateInstaller.parseExpectedChecksum(
                HASH + "  release/" + PACKAGE + "\n", PACKAGE));
        assertThrows(IOException.class, () -> UpdateInstaller.parseExpectedChecksum(
                HASH + "  PinDB-0.3.1-macos-x64.pkg\n", PACKAGE));
    }

    @Test
    void aggregateChecksumMayContainOtherPackagesButMustContainExactTarget() throws IOException {
        String aggregate = HASH + "  PinDB-0.3.1-macos-x64.pkg\n"
                + HASH + "  " + PACKAGE + "\n"
                + HASH + "  pindb-0.3.1-1.x86_64.rpm\n";

        assertEquals(HASH, UpdateInstaller.parseExpectedChecksum(aggregate, PACKAGE).orElseThrow());
    }

    private static Map<String, Object> asset(String name) {
        return Map.of(
                "name", name,
                "browser_download_url", "https://example.invalid/" + name);
    }
}
