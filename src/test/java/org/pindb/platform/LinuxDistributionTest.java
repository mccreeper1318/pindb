package org.pindb.platform;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinuxDistributionTest {
    @Test
    void detectsFedoraWorkstationAndRpmPackaging() {
        LinuxDistribution distribution = LinuxDistribution.detect("Linux", """
                NAME=Fedora Linux
                ID=fedora
                VERSION_ID=44
                PRETTY_NAME="Fedora Linux 44 (Workstation Edition)"
                VARIANT_ID=workstation
                ID_LIKE="rhel centos fedora"
                """);

        assertEquals(LinuxDistribution.Family.FEDORA, distribution.family());
        assertEquals(LinuxPackageType.RPM, distribution.packageType().orElseThrow());
        assertFalse(distribution.immutable());
        assertTrue(distribution.automaticInstallationSupported());
        assertTrue(distribution.manualInstallCommand(Path.of("/tmp/pindb.rpm"))
                .startsWith("sudo dnf install"));
    }

    @Test
    void detectsFedoraSilverblueAsImmutable() {
        LinuxDistribution distribution = LinuxDistribution.detect("Linux", """
                ID=fedora
                PRETTY_NAME="Fedora Silverblue"
                VARIANT_ID=silverblue
                ID_LIKE="rhel centos fedora"
                """);

        assertEquals(LinuxPackageType.RPM, distribution.packageType().orElseThrow());
        assertTrue(distribution.immutable());
        assertFalse(distribution.automaticInstallationSupported());
        assertTrue(distribution.manualInstallCommand(Path.of("/tmp/pindb.rpm"))
                .startsWith("sudo rpm-ostree install"));
    }

    @Test
    void detectsUbuntuAsDebianFamily() {
        LinuxDistribution distribution = LinuxDistribution.detect("Linux", """
                ID=ubuntu
                PRETTY_NAME="Ubuntu"
                ID_LIKE=debian
                """);

        assertEquals(LinuxDistribution.Family.DEBIAN, distribution.family());
        assertEquals(LinuxPackageType.DEB, distribution.packageType().orElseThrow());
        assertTrue(distribution.automaticInstallationSupported());
    }
}
