package org.pindb.platform;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum NativePackageType {
    DEB(".deb", "deb"),
    RPM(".rpm", "rpm"),
    WINDOWS_EXE(".exe", "exe"),
    MACOS_PKG(".pkg", "pkg");

    private final String extension;
    private final String scriptValue;

    NativePackageType(String extension, String scriptValue) {
        this.extension = extension;
        this.scriptValue = scriptValue;
    }

    public String extension() {
        return extension;
    }

    public String scriptValue() {
        return scriptValue;
    }

    public boolean matchesFileName(String fileName) {
        return fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(extension);
    }

    public static Optional<NativePackageType> fromFileName(String fileName) {
        return Arrays.stream(values()).filter(type -> type.matchesFileName(fileName)).findFirst();
    }
}
