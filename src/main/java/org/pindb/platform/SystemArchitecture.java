package org.pindb.platform;

import java.util.Locale;

public enum SystemArchitecture {
    X86_64,
    AARCH64,
    UNKNOWN;

    public static SystemArchitecture current() {
        return fromOsArch(System.getProperty("os.arch", ""));
    }

    public static SystemArchitecture fromOsArch(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "amd64", "x86_64", "x64" -> X86_64;
            case "aarch64", "arm64" -> AARCH64;
            default -> UNKNOWN;
        };
    }

    public static SystemArchitecture fromAssetName(String fileName) {
        String normalized = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (normalized.contains("x86_64") || normalized.contains("amd64")) {
            return X86_64;
        }
        if (normalized.contains("aarch64") || normalized.contains("arm64")) {
            return AARCH64;
        }
        return UNKNOWN;
    }
}
