package org.pindb.service;

import org.pindb.util.AppPaths;
import org.pindb.util.MiniJson;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.prefs.Preferences;

public final class SettingsService {
    public enum Theme {
        SYSTEM("System"), LIGHT("Light"), DARK("Dark");

        private final String display;

        Theme(String display) {
            this.display = display;
        }

        @Override
        public String toString() {
            return display;
        }
    }

    private static final int MAX_RECENT_FILES = 12;
    private static final String PENDING_TAG_KEY = "updates.pendingTag";
    private static final String LEGACY_PENDING_NOTES_KEY = "updates.pendingNotes";
    private static final String PENDING_RELEASE_NOTES_FILENAME = "pending-release-notes.md";
    private static final String VERSIONED_PENDING_RELEASE_NOTES_PREFIX = "pending-release-notes-";

    private final Preferences preferences;
    private final Path pendingReleaseNotesFileOverride;

    public SettingsService() {
        this(Preferences.userNodeForPackage(SettingsService.class), null);
    }

    SettingsService(Preferences preferences, Path pendingReleaseNotesFileOverride) {
        this.preferences = Objects.requireNonNull(preferences, "preferences");
        this.pendingReleaseNotesFileOverride = pendingReleaseNotesFileOverride;
    }

    public boolean autoCheckUpdates() {
        return preferences.getBoolean("updates.autoCheck", true);
    }

    public void setAutoCheckUpdates(boolean value) {
        preferences.putBoolean("updates.autoCheck", value);
    }

    public boolean includePrereleases() {
        return preferences.getBoolean("updates.includePrereleases", false);
    }

    public void setIncludePrereleases(boolean value) {
        preferences.putBoolean("updates.includePrereleases", value);
    }

    public boolean autoOpenLastDatabase() {
        return preferences.getBoolean("launcher.autoOpenLast", false);
    }

    public void setAutoOpenLastDatabase(boolean value) {
        preferences.putBoolean("launcher.autoOpenLast", value);
    }

    public Theme theme() {
        try {
            return Theme.valueOf(preferences.get("appearance.theme", Theme.SYSTEM.name()));
        } catch (IllegalArgumentException exception) {
            return Theme.SYSTEM;
        }
    }

    public void setTheme(Theme theme) {
        preferences.put("appearance.theme", Objects.requireNonNullElse(theme, Theme.SYSTEM).name());
    }

    public long updateSnoozedUntilEpochSeconds() {
        return preferences.getLong("updates.snoozedUntil", 0L);
    }

    public void snoozeUpdatesForHours(long hours) {
        preferences.putLong("updates.snoozedUntil", Instant.now().plusSeconds(hours * 3600).getEpochSecond());
    }

    public void clearUpdateSnooze() {
        preferences.remove("updates.snoozedUntil");
    }

    public boolean updateCheckAllowedNow() {
        return Instant.now().getEpochSecond() >= updateSnoozedUntilEpochSeconds();
    }

    public List<Path> recentFiles() {
        List<Path> result = new ArrayList<>();
        try {
            Object parsed = MiniJson.parse(preferences.get("launcher.recentFiles", "[]"));
            for (Object value : MiniJson.array(parsed)) {
                Path path = Path.of(MiniJson.string(value));
                if (Files.isRegularFile(path)) {
                    result.add(path);
                }
            }
        } catch (RuntimeException ignored) {
            // A damaged preference should not prevent the launcher from opening.
        }
        return result;
    }

    public void addRecentFile(Path file) {
        LinkedHashSet<Path> ordered = new LinkedHashSet<>();
        ordered.add(file.toAbsolutePath().normalize());
        ordered.addAll(recentFiles());
        List<String> serialized = ordered.stream().limit(MAX_RECENT_FILES).map(Path::toString).toList();
        preferences.put("launcher.recentFiles", MiniJson.stringify(serialized));
        preferences.put("launcher.lastDatabase", file.toAbsolutePath().normalize().toString());
    }

    public void removeRecentFile(Path file) {
        List<String> serialized = recentFiles().stream()
                .filter(path -> !path.equals(file.toAbsolutePath().normalize()))
                .map(Path::toString)
                .toList();
        preferences.put("launcher.recentFiles", MiniJson.stringify(serialized));
    }

    public Path lastDatabase() {
        String path = preferences.get("launcher.lastDatabase", "");
        if (path.isBlank()) {
            return null;
        }
        Path result = Path.of(path);
        return Files.isRegularFile(result) ? result : null;
    }

    public void setPendingReleaseNotes(String tag, String markdown) {
        try {
            writePendingReleaseNotes(versionedPendingReleaseNotesFile(tag),
                    Objects.requireNonNullElse(markdown, ""));
        } catch (IOException | RuntimeException ignored) {
            // Release-note persistence is a fallback. It must never prevent the update itself from starting.
        }
    }

    public PendingReleaseNotes takePendingReleaseNotes(String expectedVersion) {
        Path versionedNotesFile;
        try {
            versionedNotesFile = versionedPendingReleaseNotesFile(expectedVersion);
        } catch (RuntimeException ignored) {
            return takeLegacyPendingReleaseNotes(expectedVersion);
        }

        if (Files.exists(versionedNotesFile)) {
            if (!Files.isRegularFile(versionedNotesFile)) {
                return null;
            }
            try {
                String notes = Files.readString(versionedNotesFile, StandardCharsets.UTF_8);
                deleteQuietly(versionedNotesFile);
                return new PendingReleaseNotes(expectedVersion, notes);
            } catch (IOException | RuntimeException ignored) {
                // Keep the target-specific file so a later startup can retry the read.
                return null;
            }
        }

        return takeLegacyPendingReleaseNotes(expectedVersion);
    }

    private PendingReleaseNotes takeLegacyPendingReleaseNotes(String expectedVersion) {
        String tag;
        String preferenceNotes;
        try {
            tag = preferences.get(PENDING_TAG_KEY, "");
            preferenceNotes = preferences.get(LEGACY_PENDING_NOTES_KEY, null);
        } catch (RuntimeException ignored) {
            return null;
        }
        if (!sameVersion(tag, expectedVersion)) {
            return null;
        }

        Path legacyNotesFile = legacyPendingReleaseNotesFile();
        if (Files.exists(legacyNotesFile)) {
            if (Files.isRegularFile(legacyNotesFile)) {
                try {
                    String notes = Files.readString(legacyNotesFile, StandardCharsets.UTF_8);
                    clearPendingReleaseNotePreferences();
                    deleteQuietly(legacyNotesFile);
                    return new PendingReleaseNotes(tag, notes);
                } catch (IOException | RuntimeException ignored) {
                    if (preferenceNotes == null) {
                        // The file is the only copy of the notes. Preserve it and the tag for a later retry.
                        return null;
                    }
                }
            } else if (preferenceNotes == null) {
                return null;
            }
        }

        if (preferenceNotes != null) {
            clearPendingReleaseNotePreferences();
            deleteQuietly(legacyNotesFile);
            return new PendingReleaseNotes(tag, preferenceNotes);
        }
        return null;
    }

    private static boolean sameVersion(String left, String right) {
        if (left == null || left.isBlank() || right == null || right.isBlank()) {
            return false;
        }
        try {
            return Version.parse(left).equals(Version.parse(right));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private Path versionedPendingReleaseNotesFile(String version) {
        String normalized = Version.parse(version).normalized();
        Path legacy = legacyPendingReleaseNotesFile();
        return legacy.resolveSibling(VERSIONED_PENDING_RELEASE_NOTES_PREFIX + normalized + ".md");
    }

    private Path legacyPendingReleaseNotesFile() {
        if (pendingReleaseNotesFileOverride != null) {
            return pendingReleaseNotesFileOverride;
        }
        return AppPaths.stateDirectory().resolve(PENDING_RELEASE_NOTES_FILENAME);
    }

    private static void writePendingReleaseNotes(Path destination, String markdown) throws IOException {
        Path parent = destination.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        String prefix = destination.getFileName().toString() + ".";
        Path temporary = parent == null
                ? Files.createTempFile(prefix, ".tmp")
                : Files.createTempFile(parent, prefix, ".tmp");
        try {
            Files.writeString(temporary, markdown, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, destination,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void clearPendingReleaseNotePreferences() {
        try {
            preferences.remove(PENDING_TAG_KEY);
            preferences.remove(LEGACY_PENDING_NOTES_KEY);
        } catch (RuntimeException ignored) {
            // Preference cleanup is best-effort and should not interfere with updating or startup.
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException | RuntimeException ignored) {
            // Best-effort cleanup only.
        }
    }

    public record PendingReleaseNotes(String tag, String markdown) {
    }
}
