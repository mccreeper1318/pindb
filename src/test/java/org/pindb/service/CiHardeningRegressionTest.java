package org.pindb.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CiHardeningRegressionTest {
    private static final String CHECKOUT_SHA = "3d3c42e5aac5ba805825da76410c181273ba90b1";
    private static final String SETUP_JAVA_SHA = "d1374f8ac74474200d16ef8ecee22c7ae7a51880";
    private static final String UPLOAD_ARTIFACT_SHA = "cf430e030ddbb5b0abf93d22962f4752f3646cd9";
    private static final String DOWNLOAD_ARTIFACT_SHA = "9000827ccba6bdab643e8b6fd33ac0654aef8333";

    @Test
    void verificationEnforcesMinimumLineCoverage() throws Exception {
        String workflow = workflow(".github/workflows/ci.yml");
        String verifier = Files.readString(Path.of("scripts/verify-jacoco-coverage.py"));

        assertTrue(workflow.contains("Enforce JaCoCo line coverage baseline"));
        assertTrue(workflow.contains("verify-jacoco-coverage.py"));
        assertTrue(workflow.contains("jacocoTestReport.xml 0.27"));
        assertTrue(verifier.contains("counter.get(\"covered\""));
        assertTrue(verifier.contains("ratio < minimum"));
    }

    @Test
    void ciRunsCoverageJavaFxAndPackageLifecycleGates() throws Exception {
        String linux = workflow(".github/workflows/ci.yml");
        String macos = workflow(".github/workflows/macos-ci.yml");
        String windows = workflow(".github/workflows/windows-ci.yml");

        assertTrue(linux.contains("clean check packageDeb"));
        assertTrue(linux.contains("clean check packageRpm"));
        assertTrue(macos.contains("clean check packageMacPkg"));
        assertTrue(windows.contains("clean check packageWindows"));

        assertTrue(linux.contains("PINDB_RUN_JAVAFX_TESTS"));
        assertTrue(linux.contains("xvfb-run -a"));
        assertTrue(linux.contains("InWindowMenuBarUiTest"));
        assertTrue(linux.contains("maven-central.storage-download.googleapis.com/maven2/"));
        assertTrue(linux.contains("--init-script"));

        assertTrue(linux.contains("Smoke-test Debian install, upgrade, and uninstall"));
        assertTrue(linux.contains("scripts/deb-package-lifecycle-smoke.sh"));
        assertTrue(linux.contains("Smoke-test RPM install, upgrade, and uninstall"));
        assertTrue(linux.contains("scripts/rpm-package-lifecycle-smoke.sh"));
    }

    @Test
    void linuxPackageLifecycleScriptsExerciseInstallUpgradeAndRemoval() throws Exception {
        String deb = Files.readString(Path.of("scripts/deb-package-lifecycle-smoke.sh"));
        String rpm = Files.readString(Path.of("scripts/rpm-package-lifecycle-smoke.sh"));

        assertTrue(deb.contains("apt-get install -y"));
        assertTrue(deb.contains("apt-get purge -y pindb"));
        assertTrue(deb.contains("candidate_version"));
        assertTrue(rpm.contains("dnf install -y"));
        assertTrue(rpm.contains("dnf upgrade -y"));
        assertTrue(rpm.contains("dnf remove -y pindb"));
        assertTrue(rpm.contains("candidate_version"));
    }

    @Test
    void workflowsPinFedoraAndGithubActions() throws Exception {
        List<String> workflows = List.of(
                ".github/workflows/ci.yml",
                ".github/workflows/macos-ci.yml",
                ".github/workflows/windows-ci.yml",
                ".github/workflows/release.yml",
                ".github/workflows/macos-release.yml",
                ".github/workflows/windows-release.yml");

        for (String path : workflows) {
            String workflow = workflow(path);
            assertFalse(workflow.contains("uses: actions/checkout@v"), path);
            assertFalse(workflow.contains("uses: actions/setup-java@v"), path);
            assertFalse(workflow.contains("uses: actions/upload-artifact@v"), path);
            assertFalse(workflow.contains("uses: actions/download-artifact@v"), path);

            if (workflow.contains("actions/checkout@")) {
                assertTrue(workflow.contains("actions/checkout@" + CHECKOUT_SHA), path);
            }
            if (workflow.contains("actions/setup-java@")) {
                assertTrue(workflow.contains("actions/setup-java@" + SETUP_JAVA_SHA), path);
            }
            if (workflow.contains("actions/upload-artifact@")) {
                assertTrue(workflow.contains("actions/upload-artifact@" + UPLOAD_ARTIFACT_SHA), path);
            }
            if (workflow.contains("actions/download-artifact@")) {
                assertTrue(workflow.contains("actions/download-artifact@" + DOWNLOAD_ARTIFACT_SHA), path);
            }
        }

        String linuxCi = workflow(".github/workflows/ci.yml");
        String release = workflow(".github/workflows/release.yml");
        assertTrue(linuxCi.contains("image: fedora:45"));
        assertTrue(release.contains("image: fedora:45"));
        assertFalse(linuxCi.contains("fedora:latest"));
        assertFalse(release.contains("fedora:latest"));
    }

    @Test
    void highRiskBackupAndInstallerRegressionsRemainCovered() throws Exception {
        String databaseTests = Files.readString(Path.of(
                "src/test/java/org/pindb/db/DatabaseServiceTest.java"));
        String windowsTests = Files.readString(Path.of(
                "src/test/java/org/pindb/service/WindowsInstallerSafetyRegressionTest.java"));

        assertTrue(databaseTests.contains("restoresOnlyRetainedSnapshotWhenBackupLimitIsOne"));
        assertTrue(databaseTests.contains("restoresSnapshotWhileRetentionSetIsFull"));
        assertTrue(databaseTests.contains("restoresOldestSnapshotBeforePruningItFromFullRetentionSet"));
        assertTrue(windowsTests.contains("windowsInstallerUsesDedicatedPerUserApplicationDirectory"));
        assertTrue(windowsTests.contains("safetySmokeTestUsesTheSameIsolatedDirectoryAndUpgradeLineage"));
    }

    private static String workflow(String path) throws Exception {
        return Files.readString(Path.of(path)).replace("\r\n", "\n").replace('\r', '\n');
    }
}
