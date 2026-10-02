package org.pindb.platform;

import java.util.Locale;

public enum OperatingSystem {
    WINDOWS,
    MACOS,
    LINUX,
    OTHER;

    public static OperatingSystem current() {
        return fromOsName(System.getProperty("os.name", ""));
    }

    public static OperatingSystem fromOsName(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("mac") || normalized.contains("darwin")) {
            return MACOS;
        }
        if (normalized.contains("win")) {
            return WINDOWS;
        }
        if (normalized.contains("linux")) {
            return LINUX;
        }
        return OTHER;
    }
}
