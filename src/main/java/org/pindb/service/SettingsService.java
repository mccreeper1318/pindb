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
        Path notesFile = null;
        try {
            notesFile = pendingReleaseNotesFile();
            writePendingReleaseNotes(notesFile, Objects.requireNonNullElse(markdown, ""));
            preferences.put(PENDING_TAG_KEY, Objects.requireNonNullElse(tag, ""));
            preferences.remove(LEGACY_PENDING_NOTES_KEY);
        } catch (IOException | RuntimeException ignored) {
            clearPendingReleaseNotePreferences();
            deleteQuietly(notesFile);
            // Release-note persistence is a fallback. It must never prevent the update itself from starting.
        }
    }

    public PendingReleaseNotes takePendingReleaseNotes(String expectedVersion) {
        String tag = preferences.get(PENDING_TAG_KEY, "");
        if (!sameVersion(tag, expectedVersion)) {
            return null;
        }

        String notes = preferences.get(LEGACY_PENDING_NOTES_KEY, "");
        Path notesFile = null;
        try {
            notesFile = pendingReleaseNotesFile();
            if (Files.isRegularFile(notesFile)) {
                notes = Files.readString(notesFile, StandardCharsets.UTF_8);
            }
        } catch (IOException | RuntimeException ignored) {
            // Fall back to legacy preference-backed notes when available.
        } finally {
            clearPendingReleaseNotePreferences();
            deleteQuietly(notesFile);
        }

        return tag.isBlank() && notes.isBlank() ? null : new PendingReleaseNotes(tag, notes);
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

    private Path pendingReleaseNotesFile() {
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

        Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
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
