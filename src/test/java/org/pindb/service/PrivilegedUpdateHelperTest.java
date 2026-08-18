package org.pindb.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.pindb.platform.LinuxPackageType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledOnOs(OS.LINUX)
class PrivilegedUpdateHelperTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void keepsReadingOpenedPackageWhenSourcePathIsReplaced() throws Exception {
        Path source = temporaryDirectory.resolve("pindb.rpm");
        Path replacement = temporaryDirectory.resolve("replacement.rpm");
        byte[] trusted = "trusted package bytes".getBytes(StandardCharsets.UTF_8);
        Files.write(source, trusted);
        Files.writeString(replacement, "untrusted replacement");
        Path stagingRoot = Files.createDirectory(temporaryDirectory.resolve("root-stage"));

        try (PrivilegedUpdateHelper.StagedPackage staged = PrivilegedUpdateHelper.stagePackage(
                source, sha256(trusted), LinuxPackageType.RPM, stagingRoot,
                () -> Files.move(replacement, source, java.nio.file.StandardCopyOption.REPLACE_EXISTING))) {
            assertEquals("trusted package bytes", Files.readString(staged.packageFile()));
            assertEquals("untrusted replacement", Files.readString(source));
        }
    }

    @Test
    void refusesPackagePathSubstitutedWithSymbolicLink() throws Exception {
        Path target = temporaryDirectory.resolve("replacement.deb");
        Path source = temporaryDirectory.resolve("pindb.deb");
        byte[] trusted = "trusted package bytes".getBytes(StandardCharsets.UTF_8);
        Files.write(source, trusted);
        String desktopDigest = sha256(trusted);
        Files.writeString(target, "untrusted symlink target");
        Files.delete(source);
        Files.createSymbolicLink(source, target.getFileName());
        Path stagingRoot = Files.createDirectory(temporaryDirectory.resolve("root-stage"));

        IOException failure = assertThrows(IOException.class, () -> PrivilegedUpdateHelper.stagePackage(
                source, desktopDigest, LinuxPackageType.DEB, stagingRoot, () -> { }));

        assertTrue(failure.getMessage().contains("regular file"));
        try (var entries = Files.list(stagingRoot)) {
            assertFalse(entries.findAny().isPresent());
        }
    }

    @Test
    void rejectsPackageReplacedAfterDesktopChecksumBeforePrivilegedOpen() throws Exception {
        Path source = temporaryDirectory.resolve("pindb.rpm");
        byte[] trusted = "trusted package bytes".getBytes(StandardCharsets.UTF_8);
        Files.write(source, trusted);
        String desktopDigest = sha256(trusted);
        Files.writeString(source, "replacement after desktop verification");
        Path stagingRoot = Files.createDirectory(temporaryDirectory.resolve("root-stage"));
        AtomicBoolean installerCalled = new AtomicBoolean();

        IOException failure = assertThrows(IOException.class, () ->
                PrivilegedUpdateHelper.installVerifiedPackage(source, desktopDigest, LinuxPackageType.RPM,
                        stagingRoot, (staged, type) -> installerCalled.set(true), () -> { }));

        assertTrue(failure.getMessage().contains("SHA-256"));
        assertFalse(installerCalled.get());
        try (var entries = Files.list(stagingRoot)) {
            assertFalse(entries.findAny().isPresent());
        }
    }

    @Test
    void packageManagerReceivesOnlyVerifiedStagedCopy() throws Exception {
        Path source = temporaryDirectory.resolve("pindb.deb");
        byte[] trusted = "trusted package bytes".getBytes(StandardCharsets.UTF_8);
        Files.write(source, trusted);
        Path stagingRoot = Files.createDirectory(temporaryDirectory.resolve("root-stage"));
        AtomicBoolean installerCalled = new AtomicBoolean();

        PrivilegedUpdateHelper.installVerifiedPackage(source, sha256(trusted), LinuxPackageType.DEB,
                stagingRoot, (staged, type) -> {
                    installerCalled.set(true);
                    Files.writeString(source, "replacement immediately before install");
                    assertTrue(staged.startsWith(stagingRoot));
                    assertEquals("trusted package bytes", Files.readString(staged));
                    assertEquals(LinuxPackageType.DEB, type);
                }, () -> { });

        assertTrue(installerCalled.get());
        try (var entries = Files.list(stagingRoot)) {
            assertFalse(entries.findAny().isPresent());
        }
    }

    private static String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }
}
