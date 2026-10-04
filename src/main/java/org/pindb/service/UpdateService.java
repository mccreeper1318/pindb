package org.pindb.service;

import org.pindb.AppVersion;
import org.pindb.platform.LinuxDistribution;
import org.pindb.platform.NativePackageType;
import org.pindb.platform.OperatingSystem;
import org.pindb.platform.SystemArchitecture;
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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class UpdateService {
    public static final String REPOSITORY = "mccreeper1318/pindb";
    private static final URI RELEASES_API = URI.create("https://api.github.com/repos/" + REPOSITORY + "/releases");

    private final HttpClient client;
    private final LinuxDistribution distribution;
    private final SystemArchitecture architecture;
    private final OperatingSystem operatingSystem;

    public UpdateService() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NORMAL).build(),
                LinuxDistribution.current(), SystemArchitecture.current(), OperatingSystem.current());
    }

    UpdateService(HttpClient client, LinuxDistribution distribution, SystemArchitecture architecture) {
        this(client, distribution, architecture, OperatingSystem.current());
    }

    UpdateService(HttpClient client, LinuxDistribution distribution, SystemArchitecture architecture,
                  OperatingSystem operatingSystem) {
        this.client = client;
        this.distribution = distribution;
        this.architecture = architecture;
        this.operatingSystem = operatingSystem == null ? OperatingSystem.OTHER : operatingSystem;
    }

    public Optional<ReleaseInfo> checkForUpdate(boolean includePrereleases) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(RELEASES_API).timeout(Duration.ofSeconds(30))
                .header("Accept", "application/vnd.github+json").header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "PinDB/" + AppVersion.VERSION).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IOException("GitHub returned HTTP " + response.statusCode() + " while checking for updates.");
        Version current = Version.parse(AppVersion.VERSION);
        return MiniJson.array(MiniJson.parse(response.body())).stream().map(MiniJson::object)
                .filter(release -> !MiniJson.bool(release.get("draft")))
                .filter(release -> includePrereleases || !MiniJson.bool(release.get("prerelease")))
                .map(this::toRelease).flatMap(Optional::stream)
                .filter(release -> release.version().compareTo(current) > 0)
                .max(Comparator.comparing(ReleaseInfo::version));
    }

    private Optional<ReleaseInfo> toRelease(Map<String, Object> release) {
        try {
            Optional<NativePackageType> packageType = currentPackageType();
            if (packageType.isEmpty()) return Optional.empty();
            String tag = MiniJson.string(release.get("tag_name"));
            Version version = Version.parse(tag);
            List<Map<String, Object>> assets = MiniJson.array(release.get("assets")).stream().map(MiniJson::object).toList();
            Optional<ReleasePackage> releasePackage = selectPackage(assets, packageType.get(), architecture);
            if (releasePackage.isEmpty()) return Optional.empty();
            String releaseName = MiniJson.string(release.get("name"));
            return Optional.of(new ReleaseInfo(tag, version, releaseName.isBlank() ? "PinDB " + tag : releaseName,
                    MiniJson.string(release.get("body")), releasePackage.get(), MiniJson.bool(release.get("prerelease")),
                    parseInstant(MiniJson.string(release.get("published_at")))));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private Optional<NativePackageType> currentPackageType() {
        return packageTypeFor(operatingSystem, distribution);
    }

    static Optional<NativePackageType> packageTypeFor(OperatingSystem operatingSystem,
                                                      LinuxDistribution distribution) {
        return switch (operatingSystem == null ? OperatingSystem.OTHER : operatingSystem) {
            case WINDOWS -> Optional.of(NativePackageType.WINDOWS_EXE);
            case MACOS -> Optional.of(NativePackageType.MACOS_PKG);
            case LINUX -> distribution == null ? Optional.empty() : distribution.packageType();
            case OTHER -> Optional.empty();
        };
    }

    static Optional<ReleasePackage> selectPackage(List<Map<String, Object>> assets, NativePackageType packageType,
                                                   SystemArchitecture architecture) {
        List<ReleasePackage> candidates = assets.stream().map(asset -> toPackage(asset, assets, packageType)).flatMap(Optional::stream).toList();
        if (architecture == SystemArchitecture.UNKNOWN) {
            return packageType == NativePackageType.MACOS_PKG ? Optional.empty() : candidates.stream().findFirst();
        }
        Optional<ReleasePackage> exact = candidates.stream().filter(candidate -> candidate.architecture() == architecture).findFirst();
        if (exact.isPresent() || packageType == NativePackageType.MACOS_PKG) return exact;
        return candidates.stream().filter(candidate -> candidate.architecture() == SystemArchitecture.UNKNOWN).findFirst();
    }

    private static Optional<ReleasePackage> toPackage(Map<String, Object> asset, List<Map<String, Object>> allAssets,
                                                       NativePackageType packageType) {
        String name = MiniJson.string(asset.get("name"));
        String lowerName = name.toLowerCase(Locale.ROOT);
        if (!lowerName.contains("pindb") || !packageType.matchesFileName(name)) return Optional.empty();
        String downloadUrl = MiniJson.string(asset.get("browser_download_url"));
        if (downloadUrl.isBlank()) return Optional.empty();
        URI checksumUri = findChecksum(allAssets, name, packageType).map(checksum -> MiniJson.string(checksum.get("browser_download_url")))
                .filter(url -> !url.isBlank()).map(URI::create).orElse(null);
        return Optional.of(new ReleasePackage(packageType, packageArchitecture(name, packageType), name, URI.create(downloadUrl), checksumUri));
    }

    static SystemArchitecture packageArchitecture(String packageName, NativePackageType packageType) {
        if (packageType != NativePackageType.MACOS_PKG) return SystemArchitecture.fromAssetName(packageName);
        String lowerName = packageName == null ? "" : packageName.toLowerCase(Locale.ROOT);
        if (lowerName.endsWith("-macos-arm64.pkg")) return SystemArchitecture.AARCH64;
        if (lowerName.endsWith("-macos-x64.pkg")) return SystemArchitecture.X86_64;
        return SystemArchitecture.UNKNOWN;
    }

    private static Optional<Map<String, Object>> findChecksum(List<Map<String, Object>> assets, String packageName,
                                                               NativePackageType packageType) {
        String expected = (packageName + ".sha256").toLowerCase(Locale.ROOT);
        Optional<Map<String, Object>> exact = assets.stream()
                .filter(asset -> MiniJson.string(asset.get("name")).toLowerCase(Locale.ROOT).equals(expected))
                .findFirst();
        if (exact.isPresent()) return exact;

        String platformAggregate = switch (packageType) {
            case DEB, RPM -> "checksums-linux.sha256";
            case WINDOWS_EXE -> "checksums-windows.sha256";
            case MACOS_PKG -> "checksums-macos.sha256";
        };
        Optional<Map<String, Object>> platformChecksum = assets.stream()
                .filter(asset -> MiniJson.string(asset.get("name")).equalsIgnoreCase(platformAggregate))
                .findFirst();
        if (platformChecksum.isPresent()) return platformChecksum;

        return assets.stream()
                .filter(asset -> MiniJson.string(asset.get("name")).equalsIgnoreCase("checksums.sha256"))
                .findFirst();
    }

    private static Instant parseInstant(String value) {
        try { return value == null || value.isBlank() ? Instant.EPOCH : Instant.parse(value); }
        catch (DateTimeParseException exception) { return Instant.EPOCH; }
    }
}
