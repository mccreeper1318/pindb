package org.pindb.service;

import org.pindb.AppVersion;
import org.pindb.util.AppPaths;
import org.pindb.util.MiniJson;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ReleaseHistoryService {
    private static final URI RELEASES_API = URI.create(
            "https://api.github.com/repos/" + UpdateService.REPOSITORY + "/releases?per_page=100");
    private static final Path CACHE_FILE = AppPaths.cacheDirectory().resolve("release-history.json");
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public List<ReleaseNote> loadCachedOrBundled() {
        List<ReleaseNote> cached = readCache();
        return cached.isEmpty() ? bundledHistory() : merge(cached, bundledHistory());
    }

    public List<ReleaseNote> refresh() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(RELEASES_API)
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "PinDB/" + AppVersion.VERSION)
                .GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("GitHub returned HTTP " + response.statusCode()
                    + " while loading release history.");
        }
        List<ReleaseNote> online = parseReleases(response.body());
        Files.writeString(CACHE_FILE, response.body(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return merge(online, bundledHistory());
    }

    static List<ReleaseNote> parseReleases(String json) {
        List<ReleaseNote> notes = new ArrayList<>();
        for (Object value : MiniJson.array(MiniJson.parse(json))) {
            Map<String, Object> release = MiniJson.object(value);
            if (MiniJson.bool(release.get("draft"))) {
                continue;
            }
            String tag = MiniJson.string(release.get("tag_name"));
            if (tag.isBlank()) {
                continue;
            }
            String title = MiniJson.string(release.get("name"));
            notes.add(new ReleaseNote(tag,
                    title.isBlank() ? "PinDB " + tag : title,
                    MiniJson.string(release.get("body")),
                    MiniJson.bool(release.get("prerelease")),
                    parseInstant(MiniJson.string(release.get("published_at"))), false));
        }
        notes.sort(releaseComparator());
        return notes;
    }

    private List<ReleaseNote> readCache() {
        try {
            return Files.isRegularFile(CACHE_FILE)
                    ? parseReleases(Files.readString(CACHE_FILE, StandardCharsets.UTF_8))
                    : List.of();
        } catch (RuntimeException | IOException ignored) {
            return List.of();
        }
    }

    private List<ReleaseNote> bundledHistory() {
        try (InputStream input = getClass().getResourceAsStream("/org/pindb/app/CHANGELOG.md")) {
            if (input == null) {
                return List.of();
            }
            String changelog = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            return parseBundledChangelog(changelog);
        } catch (IOException exception) {
            return List.of();
        }
    }

    static List<ReleaseNote> parseBundledChangelog(String changelog) {
        List<ReleaseNote> result = new ArrayList<>();
        String currentTag = null;
        StringBuilder body = new StringBuilder();
        for (String line : (changelog == null ? "" : changelog).replace("\r\n", "\n").split("\n", -1)) {
            if (line.startsWith("## ")) {
                if (currentTag != null) {
                    result.add(bundled(currentTag, body.toString().trim()));
                }
                currentTag = line.substring(3).trim()
                        .replaceFirst("^\\[", "").replaceFirst("](?:\\s+-.*)?$", "");
                body.setLength(0);
            } else if (currentTag != null) {
                body.append(line).append('\n');
            }
        }
        if (currentTag != null) {
            result.add(bundled(currentTag, body.toString().trim()));
        }
        result.sort(releaseComparator());
        return result;
    }

    private static ReleaseNote bundled(String tag, String body) {
        String markdown = body.isBlank() ? "No bundled release notes are available." : body;
        return new ReleaseNote(tag, "PinDB " + tag, markdown,
                tag.toLowerCase().contains("beta") || tag.toLowerCase().contains("alpha"),
                Instant.EPOCH, true);
    }

    private static List<ReleaseNote> merge(List<ReleaseNote> primary, List<ReleaseNote> fallback) {
        LinkedHashMap<String, ReleaseNote> merged = new LinkedHashMap<>();
        primary.forEach(note -> merged.put(note.tag(), note));
        fallback.forEach(note -> merged.putIfAbsent(note.tag(), note));
        return merged.values().stream().sorted(releaseComparator()).toList();
    }

    private static Comparator<ReleaseNote> releaseComparator() {
        return (left, right) -> {
            try {
                return Version.parse(right.tag()).compareTo(Version.parse(left.tag()));
            } catch (RuntimeException ignored) {
                return right.tag().compareToIgnoreCase(left.tag());
            }
        };
    }

    private static Instant parseInstant(String value) {
        try {
            return value == null || value.isBlank() ? Instant.EPOCH : Instant.parse(value);
        } catch (DateTimeParseException exception) {
            return Instant.EPOCH;
        }
    }
}
