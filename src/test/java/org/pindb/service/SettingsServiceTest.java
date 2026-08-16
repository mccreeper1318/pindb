package org.pindb.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsServiceTest {
    @TempDir
    Path tempDirectory;

    @Test
    void pendingReleaseNotesSupportBodiesLargerThanPreferencesLimit() throws Exception {
        Preferences preferences = Preferences.userRoot().node("org/pindb/test/" + System.nanoTime());
        Path legacyNotesFile = tempDirectory.resolve("pending-release-notes.md");
        Path notesFile = tempDirectory.resolve("pending-release-notes-0.2.1-beta.4.md");
        SettingsService settings = new SettingsService(preferences, legacyNotesFile);
        String markdown = "x".repeat(Preferences.MAX_VALUE_LENGTH + 4096);

        try {
            settings.setPendingReleaseNotes("0.2.1-beta.4", markdown);

            assertNull(preferences.get("updates.pendingTag", null));
            assertNull(preferences.get("updates.pendingNotes", null));
            assertEquals(markdown, Files.readString(notesFile));

            SettingsService.PendingReleaseNotes pending = settings.takePendingReleaseNotes("0.2.1-beta.4");
            assertNotNull(pending);
            assertEquals("0.2.1-beta.4", pending.tag());
            assertEquals(markdown, pending.markdown());
            assertFalse(Files.exists(notesFile));
        } finally {
            preferences.removeNode();
        }
    }

    @Test
    void pendingReleaseNotesFallBackToLegacyPreferenceStorage() throws Exception {
        Preferences preferences = Preferences.userRoot().node("org/pindb/test/" + System.nanoTime());
        Path notesFile = tempDirectory.resolve("missing-pending-release-notes.md");
        SettingsService settings = new SettingsService(preferences, notesFile);

        try {
            preferences.put("updates.pendingTag", "0.2.1-beta.3");
            preferences.put("updates.pendingNotes", "legacy notes");

            SettingsService.PendingReleaseNotes pending = settings.takePendingReleaseNotes("0.2.1-beta.3");
            assertNotNull(pending);
            assertEquals("0.2.1-beta.3", pending.tag());
            assertEquals("legacy notes", pending.markdown());
            assertNull(preferences.get("updates.pendingTag", null));
            assertNull(preferences.get("updates.pendingNotes", null));
        } finally {
            preferences.removeNode();
        }
    }

    @Test
    void pendingReleaseNotesArePreservedForNonTargetVersion() throws Exception {
        Preferences preferences = Preferences.userRoot().node("org/pindb/test/" + System.nanoTime());
        Path legacyNotesFile = tempDirectory.resolve("pending-release-notes-preserved.md");
        Path notesFile = tempDirectory.resolve("pending-release-notes-0.2.1-beta.4.md");
        SettingsService settings = new SettingsService(preferences, legacyNotesFile);

        try {
            settings.setPendingReleaseNotes("0.2.1-beta.4", "target notes");

            SettingsService.PendingReleaseNotes pending = settings.takePendingReleaseNotes("0.2.1-beta.3");
            assertNull(pending);
            assertTrue(Files.isRegularFile(notesFile));
            assertEquals("target notes", Files.readString(notesFile));

            SettingsService.PendingReleaseNotes target = settings.takePendingReleaseNotes("v0.2.1-beta.4");
            assertNotNull(target);
            assertEquals("v0.2.1-beta.4", target.tag());
            assertEquals("target notes", target.markdown());
            assertFalse(Files.exists(notesFile));
        } finally {
            preferences.removeNode();
        }
    }

    @Test
    void differentPendingVersionsKeepIndependentNotesFiles() throws Exception {
        Preferences preferences = Preferences.userRoot().node("org/pindb/test/" + System.nanoTime());
        Path legacyNotesFile = tempDirectory.resolve("pending-release-notes-concurrent.md");
        Path notesA = tempDirectory.resolve("pending-release-notes-0.2.1-beta.4.md");
        Path notesB = tempDirectory.resolve("pending-release-notes-0.2.1-beta.5.md");
        SettingsService settings = new SettingsService(preferences, legacyNotesFile);

        try {
            settings.setPendingReleaseNotes("0.2.1-beta.4", "release A notes");
            settings.setPendingReleaseNotes("0.2.1-beta.5", "release B notes");

            assertEquals("release A notes", Files.readString(notesA));
            assertEquals("release B notes", Files.readString(notesB));

            SettingsService.PendingReleaseNotes releaseA = settings.takePendingReleaseNotes("0.2.1-beta.4");
            assertNotNull(releaseA);
            assertEquals("release A notes", releaseA.markdown());
            assertFalse(Files.exists(notesA));
            assertTrue(Files.exists(notesB));

            SettingsService.PendingReleaseNotes releaseB = settings.takePendingReleaseNotes("0.2.1-beta.5");
            assertNotNull(releaseB);
            assertEquals("release B notes", releaseB.markdown());
            assertFalse(Files.exists(notesB));
        } finally {
            preferences.removeNode();
        }
    }

    @Test
    void unreadableVersionedNotesArePreservedForRetry() throws Exception {
        Preferences preferences = Preferences.userRoot().node("org/pindb/test/" + System.nanoTime());
        Path legacyNotesFile = tempDirectory.resolve("pending-release-notes-read-failure.md");
        Path notesFile = tempDirectory.resolve("pending-release-notes-0.2.1-beta.4.md");
        SettingsService settings = new SettingsService(preferences, legacyNotesFile);

        try {
            Files.write(notesFile, new byte[]{(byte) 0xC3, (byte) 0x28});

            SettingsService.PendingReleaseNotes firstAttempt = settings.takePendingReleaseNotes("0.2.1-beta.4");
            assertNull(firstAttempt);
            assertTrue(Files.exists(notesFile));

            Files.writeString(notesFile, "recovered notes");
            SettingsService.PendingReleaseNotes retry = settings.takePendingReleaseNotes("0.2.1-beta.4");
            assertNotNull(retry);
            assertEquals("recovered notes", retry.markdown());
            assertFalse(Files.exists(notesFile));
        } finally {
            preferences.removeNode();
        }
    }

    @Test
    void unreadableLegacyFileKeepsFallbackUntilRetryWhenNoPreferenceNotesExist() throws Exception {
        Preferences preferences = Preferences.userRoot().node("org/pindb/test/" + System.nanoTime());
        Path legacyNotesFile = tempDirectory.resolve("pending-release-notes-legacy-read-failure.md");
        SettingsService settings = new SettingsService(preferences, legacyNotesFile);

        try {
            preferences.put("updates.pendingTag", "0.2.1-beta.4");
            Files.write(legacyNotesFile, new byte[]{(byte) 0xC3, (byte) 0x28});

            SettingsService.PendingReleaseNotes firstAttempt = settings.takePendingReleaseNotes("0.2.1-beta.4");
            assertNull(firstAttempt);
            assertEquals("0.2.1-beta.4", preferences.get("updates.pendingTag", ""));
            assertTrue(Files.exists(legacyNotesFile));

            Files.writeString(legacyNotesFile, "legacy recovered notes");
            SettingsService.PendingReleaseNotes retry = settings.takePendingReleaseNotes("0.2.1-beta.4");
            assertNotNull(retry);
            assertEquals("legacy recovered notes", retry.markdown());
            assertNull(preferences.get("updates.pendingTag", null));
            assertFalse(Files.exists(legacyNotesFile));
        } finally {
            preferences.removeNode();
        }
    }
}
