package org.pindb.platform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class LinuxDistribution {
    public enum Family {
        DEBIAN,
        FEDORA,
        OTHER_LINUX,
        NON_LINUX
    }

    private static final Set<String> DEBIAN_IDS = Set.of(
            "debian", "ubuntu", "linuxmint", "pop", "elementary", "zorin", "kali", "raspbian", "neon");
    private static final Set<String> FEDORA_IDS = Set.of(
            "fedora", "rhel", "centos", "rocky", "almalinux", "nobara", "ultramarine");
    private static final Set<String> IMMUTABLE_VARIANTS = Set.of(
            "silverblue", "kinoite", "sericea", "onyx", "coreos", "iot", "atomic");

    private final String id;
    private final String variantId;
    private final String prettyName;
    private final Set<String> idLike;
    private final Family family;
    private final boolean immutable;

    private LinuxDistribution(String id, String variantId, String prettyName,
                              Set<String> idLike, Family family, boolean immutable) {
        this.id = id;
        this.variantId = variantId;
        this.prettyName = prettyName;
        this.idLike = Collections.unmodifiableSet(new LinkedHashSet<>(idLike));
        this.family = family;
        this.immutable = immutable;
    }

    public static LinuxDistribution current() {
        String osName = System.getProperty("os.name", "");
        if (!osName.toLowerCase(Locale.ROOT).contains("linux")) {
            return detect(osName, "");
        }
        for (Path path : ListHolder.OS_RELEASE_PATHS) {
            try {
                if (Files.isRegularFile(path)) {
                    return detect(osName, Files.readString(path));
                }
            } catch (IOException ignored) {
                // Try the next standard os-release location.
            }
        }
        return detect(osName, "");
    }

    public static LinuxDistribution detect(String osName, String osReleaseText) {
        if (osName == null || !osName.toLowerCase(Locale.ROOT).contains("linux")) {
            return new LinuxDistribution("", "", osName == null ? "Unknown operating system" : osName,
                    Set.of(), Family.NON_LINUX, false);
        }
        Map<String, String> values = parseOsRelease(osReleaseText);
        String id = normalized(values.get("ID"));
        String variantId = normalized(values.get("VARIANT_ID"));
        String prettyName = values.getOrDefault("PRETTY_NAME", "Linux").trim();
        Set<String> idLike = words(values.get("ID_LIKE"));

        Family family;
        if (DEBIAN_IDS.contains(id) || idLike.stream().anyMatch(DEBIAN_IDS::contains)) {
            family = Family.DEBIAN;
        } else if (FEDORA_IDS.contains(id) || idLike.stream().anyMatch(FEDORA_IDS::contains)) {
            family = Family.FEDORA;
        } else {
            family = Family.OTHER_LINUX;
        }
        boolean immutable = family == Family.FEDORA
                && (IMMUTABLE_VARIANTS.contains(id)
                || IMMUTABLE_VARIANTS.contains(variantId)
                || idLike.stream().anyMatch(IMMUTABLE_VARIANTS::contains));
        return new LinuxDistribution(id, variantId, prettyName, idLike, family, immutable);
    }

    public String id() {
        return id;
    }

    public String variantId() {
        return variantId;
    }

    public String prettyName() {
        return prettyName;
    }

    public Set<String> idLike() {
        return idLike;
    }

    public Family family() {
        return family;
    }

    public boolean immutable() {
        return immutable;
    }

    public boolean isLinux() {
        return family != Family.NON_LINUX;
    }

    public Optional<LinuxPackageType> packageType() {
        return switch (family) {
            case DEBIAN -> Optional.of(LinuxPackageType.DEB);
            case FEDORA -> Optional.of(LinuxPackageType.RPM);
            case OTHER_LINUX, NON_LINUX -> Optional.empty();
        };
    }

    public boolean automaticInstallationSupported() {
        return (family == Family.DEBIAN || family == Family.FEDORA) && !immutable;
    }

    public String manualInstallCommand(Path packageFile) {
        String quoted = quote(packageFile.toAbsolutePath().normalize().toString());
        if (family == Family.DEBIAN) {
            return "sudo apt install " + quoted;
        }
        if (family == Family.FEDORA && immutable) {
            return "sudo rpm-ostree install " + quoted;
        }
        if (family == Family.FEDORA) {
            return "sudo dnf install " + quoted;
        }
        return "";
    }

    private static Map<String, String> parseOsRelease(String text) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String rawLine : (text == null ? "" : text).lines().toList()) {
            String line = rawLine.trim();
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            int equals = line.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            String key = line.substring(0, equals).trim();
            String value = line.substring(equals + 1).trim();
            if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'")))) {
                value = value.substring(1, value.length() - 1);
            }
            values.put(key, value.replace("\\\"", "\"").replace("\\\\", "\\"));
        }
        return values;
    }

    private static Set<String> words(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        Arrays.stream(value.toLowerCase(Locale.ROOT).split("\\s+"))
                .map(String::trim)
                .filter(token -> !token.isBlank())
                .forEach(result::add);
        return result;
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static final class ListHolder {
        private static final java.util.List<Path> OS_RELEASE_PATHS = java.util.List.of(
                Path.of("/etc/os-release"), Path.of("/usr/lib/os-release"));

        private ListHolder() {
        }
    }
}
