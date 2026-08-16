package org.pindb.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class SettingsServiceTest {
    @TempDir
    Path tempDirectory;

    @Test
    void pendingReleaseNotesSupportBodiesLargerThanPreferencesLimit() throws Exception {
        Preferences preferences = Preferences.userRoot().node("org/pindb/test/" + System.nanoTime());
        Path notesFile = tempDirectory.resolve("pending-release-notes.md");
        SettingsService settings = new SettingsService(preferences, notesFile);
        String markdown = "x".repeat(Preferences.MAX_VALUE_LENGTH + 4096);

        try {
            settings.setPendingReleaseNotes("0.2.1-beta.4", markdown);

            assertEquals("0.2.1-beta.4", preferences.get("updates.pendingTag", ""));
            assertNull(preferences.get("updates.pendingNotes", null));
            assertEquals(markdown, Files.readString(notesFile));

            SettingsService.PendingReleaseNotes pending = settings.takePendingReleaseNotes();
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

            SettingsService.PendingReleaseNotes pending = settings.takePendingReleaseNotes();
            assertEquals("0.2.1-beta.3", pending.tag());
            assertEquals("legacy notes", pending.markdown());
            assertNull(preferences.get("updates.pendingTag", null));
            assertNull(preferences.get("updates.pendingNotes", null));
        } finally {
            preferences.removeNode();
        }
    }
}
