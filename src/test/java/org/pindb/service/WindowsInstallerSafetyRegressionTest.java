package org.pindb.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindowsInstallerSafetyRegressionTest {
    private static final String WINDOWS_UPGRADE_UUID = "333e60e6-ca34-4411-9fce-6b1d6c8ff2c9";

    @Test
    void windowsInstallerUsesDedicatedPerUserApplicationDirectory() throws Exception {
        String build = Files.readString(Path.of("build.gradle"));

        assertTrue(build.contains("'--win-per-user-install'"));
        assertTrue(build.contains("'--install-dir', 'Programs\\\\PinDB'"));
        assertFalse(build.contains("'--win-dir-chooser'"));
        assertTrue(build.contains("'--win-upgrade-uuid', windowsUpgradeUuid"));
        assertTrue(build.contains("def windowsUpgradeUuid = '" + WINDOWS_UPGRADE_UUID + "'"));
    }

    @Test
    void windowsCiBuildsTheInstallerSoSafetyConfigurationCannotRot() throws Exception {
        String workflow = normalizedWorkflow(".github/workflows/windows-ci.yml");

        assertTrue(workflow.contains("Install WiX"));
        assertTrue(workflow.contains("test packageWindows"));
        assertTrue(workflow.contains("0.3.2-ci.1"));
        assertTrue(workflow.contains("Expected exactly one Windows EXE installer"));
    }

    @Test
    void updateDocumentationSeparatesApplicationFilesFromUserData() throws Exception {
        String documentation = Files.readString(Path.of("docs/update-system.md"));

        assertTrue(documentation.contains("%LOCALAPPDATA%\\Programs\\PinDB"));
        assertTrue(documentation.contains("%LOCALAPPDATA%\\PinDB"));
        assertTrue(documentation.contains("Do not run a legacy uninstaller against a directory that also contains user files."));
    }

    private static String normalizedWorkflow(String path) throws Exception {
        return Files.readString(Path.of(path)).replace("\r\n", "\n").replace('\r', '\n');
    }
}
