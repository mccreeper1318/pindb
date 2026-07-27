package org.pindb.service;

import org.pindb.AppVersion;
import org.pindb.util.MiniJson;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class UpdateService {
    public static final String REPOSITORY = "mccreeper1318/pindb";
    private static final URI RELEASES_API = URI.create("https://api.github.com/repos/" + REPOSITORY + "/releases");
    private final HttpClient client;

    public UpdateService() {
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public Optional<ReleaseInfo> checkForUpdate(boolean includePrereleases) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(RELEASES_API)
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "PinDB/" + AppVersion.VERSION)
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("GitHub returned HTTP " + response.statusCode() + " while checking for updates.");
        }
        Version current = Version.parse(AppVersion.VERSION);
        return MiniJson.array(MiniJson.parse(response.body())).stream()
                .map(MiniJson::object)
                .filter(release -> !MiniJson.bool(release.get("draft")))
                .filter(release -> includePrereleases || !MiniJson.bool(release.get("prerelease")))
                .map(this::toRelease)
                .flatMap(Optional::stream)
                .filter(release -> release.version().compareTo(current) > 0)
                .max(Comparator.comparing(ReleaseInfo::version));
    }

    private Optional<ReleaseInfo> toRelease(Map<String, Object> release) {
        try {
            String tag = MiniJson.string(release.get("tag_name"));
            Version version = Version.parse(tag);
            List<Map<String, Object>> assets = MiniJson.array(release.get("assets")).stream()
                    .map(MiniJson::object).toList();
            Map<String, Object> deb = assets.stream()
                    .filter(asset -> MiniJson.string(asset.get("name")).toLowerCase().endsWith(".deb"))
                    .filter(asset -> MiniJson.string(asset.get("name")).toLowerCase().contains("pindb"))
                    .findFirst().orElse(null);
            if (deb == null) {
                return Optional.empty();
            }
            String debName = MiniJson.string(deb.get("name"));
            Map<String, Object> checksum = assets.stream()
                    .filter(asset -> {
                        String name = MiniJson.string(asset.get("name"));
                        return name.equals(debName + ".sha256") || name.equalsIgnoreCase("checksums.sha256");
                    })
                    .findFirst().orElse(null);
            return Optional.of(new ReleaseInfo(
                    tag,
                    version,
                    MiniJson.string(release.get("name")).isBlank() ? "PinDB " + tag : MiniJson.string(release.get("name")),
                    MiniJson.string(release.get("body")),
                    URI.create(MiniJson.string(deb.get("browser_download_url"))),
                    checksum == null ? null : URI.create(MiniJson.string(checksum.get("browser_download_url"))),
                    MiniJson.bool(release.get("prerelease")),
                    parseInstant(MiniJson.string(release.get("published_at")))
            ));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private static Instant parseInstant(String value) {
        try {
            return value == null || value.isBlank() ? Instant.EPOCH : Instant.parse(value);
        } catch (DateTimeParseException exception) {
            return Instant.EPOCH;
        }
    }
}
