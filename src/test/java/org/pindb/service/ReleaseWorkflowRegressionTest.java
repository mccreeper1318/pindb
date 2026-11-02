package org.pindb.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleaseWorkflowRegressionTest {
    @Test
    void coordinatorPublishesOnlyAfterAllPlatformAssetsAreVerified() throws Exception {
        String workflow = normalizedWorkflow(".github/workflows/release.yml");

        assertTrue(workflow.contains("push:\n    tags:"));
        assertFalse(workflow.contains("types:\n      - published"));
        assertTrue(workflow.contains("gh workflow run windows-release.yml"));
        assertTrue(workflow.contains("gh workflow run macos-release.yml"));
        assertTrue(workflow.contains("-f run_key=\"$RUN_KEY\""));
        assertTrue(workflow.contains("Require dispatched platform builds to succeed"));
        assertTrue(workflow.contains("gh run watch \"$WINDOWS_RUN_ID\""));
        assertTrue(workflow.contains("gh run watch \"$MACOS_RUN_ID\""));
        assertTrue(workflow.contains("Smoke-test Debian package installation"));
        assertTrue(workflow.contains("Smoke-test Fedora RPM installation"));
        assertTrue(workflow.contains("Expected exactly 10 release assets before publication"));
        assertTrue(workflow.contains("Expected five checksum files"));
        assertTrue(workflow.contains("--draft=false"));
        assertTrue(workflow.contains("Recovering incomplete published release"));
        assertTrue(workflow.contains("gh release edit \"$RAW_TAG\" --repo \"$GITHUB_REPOSITORY\" --draft"));
        assertTrue(workflow.contains("RELEASE_ASSET_COUNT"));
        assertTrue(workflow.contains("RELEASE_IS_IMMUTABLE"));
        assertTrue(workflow.contains("(\\.[0-9]+){0,2}"),
                "The coordinator must accept four-part hotfix tags such as 0.3.1.1.");

        int platformRuns = workflow.indexOf("Require dispatched platform builds to succeed");
        int assetGate = workflow.indexOf("Wait for complete cross-platform release assets");
        int verification = workflow.indexOf("Download and verify every published asset checksum");
        int publication = workflow.indexOf("Publish fully verified release");
        assertTrue(platformRuns >= 0 && assetGate > platformRuns,
                "Current platform builds must succeed before existing assets can satisfy the release gate.");
        assertTrue(verification >= 0 && publication > verification,
                "Checksum verification must happen before the release is published.");
    }

    @Test
    void platformBuildersAreDispatchedBeforePublicationInsteadOfAfterReleasePublished() throws Exception {
        String windows = normalizedWorkflow(".github/workflows/windows-release.yml");
        String macos = normalizedWorkflow(".github/workflows/macos-release.yml");

        assertTrue(windows.contains("workflow_dispatch:"));
        assertTrue(macos.contains("workflow_dispatch:"));
        assertTrue(windows.contains("run_key:"));
        assertTrue(macos.contains("run_key:"));
        assertTrue(windows.contains("(\\.\\d+){0,2}"),
                "Windows releases must accept four-part hotfix tags.");
        assertTrue(macos.contains("(\\.[0-9]+){0,2}"),
                "macOS releases must accept four-part hotfix tags.");
        assertTrue(windows.contains("--json databaseId"));
        assertTrue(macos.contains("--json databaseId"));
        assertFalse(windows.contains("releases/tags/$TAG"));
        assertFalse(macos.contains("releases/tags/$TAG"));
        assertFalse(windows.contains("types: [published]"));
        assertFalse(windows.contains("types:\n      - published"));
        assertFalse(macos.contains("types: [published]"));
        assertFalse(macos.contains("types:\n      - published"));
    }

    private static String normalizedWorkflow(String path) throws Exception {
        return Files.readString(Path.of(path)).replace("\r\n", "\n").replace('\r', '\n');
    }
}
