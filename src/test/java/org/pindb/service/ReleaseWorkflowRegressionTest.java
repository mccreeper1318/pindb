package org.pindb.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleaseWorkflowRegressionTest {
    @Test
    void coordinatorPublishesOnlyAfterAllPlatformAssetsAreVerified() throws Exception {
        String workflow = Files.readString(Path.of(".github/workflows/release.yml"));

        assertTrue(workflow.contains("push:\n    tags:"));
        assertFalse(workflow.contains("types:\n      - published"));
        assertTrue(workflow.contains("gh workflow run windows-release.yml"));
        assertTrue(workflow.contains("gh workflow run macos-release.yml"));
        assertTrue(workflow.contains("Smoke-test Debian package installation"));
        assertTrue(workflow.contains("Smoke-test Fedora RPM installation"));
        assertTrue(workflow.contains("Expected exactly 10 release assets before publication"));
        assertTrue(workflow.contains("Expected five checksum files"));
        assertTrue(workflow.contains("--draft=false"));

        int verification = workflow.indexOf("Download and verify every published asset checksum");
        int publication = workflow.indexOf("Publish fully verified release");
        assertTrue(verification >= 0 && publication > verification,
                "Checksum verification must happen before the release is published.");
    }

    @Test
    void platformBuildersAreDispatchedBeforePublicationInsteadOfAfterReleasePublished() throws Exception {
        String windows = Files.readString(Path.of(".github/workflows/windows-release.yml"));
        String macos = Files.readString(Path.of(".github/workflows/macos-release.yml"));

        assertTrue(windows.contains("workflow_dispatch:"));
        assertTrue(macos.contains("workflow_dispatch:"));
        assertFalse(windows.contains("release:\n"));
        assertFalse(macos.contains("release:\n"));
        assertFalse(windows.contains("published"));
        assertFalse(macos.contains("published"));
    }
}
