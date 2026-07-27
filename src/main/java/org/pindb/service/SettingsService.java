package org.pindb.service;

import org.pindb.util.MiniJson;

import java.nio.file.Files;
import java.nio.file.Path;
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
    private final Preferences preferences = Preferences.userNodeForPackage(SettingsService.class);

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
        preferences.put("updates.pendingTag", Objects.requireNonNullElse(tag, ""));
        preferences.put("updates.pendingNotes", Objects.requireNonNullElse(markdown, ""));
    }

    public PendingReleaseNotes takePendingReleaseNotes() {
        String tag = preferences.get("updates.pendingTag", "");
        String notes = preferences.get("updates.pendingNotes", "");
        preferences.remove("updates.pendingTag");
        preferences.remove("updates.pendingNotes");
        return tag.isBlank() && notes.isBlank() ? null : new PendingReleaseNotes(tag, notes);
    }

    public record PendingReleaseNotes(String tag, String markdown) {
    }
}
