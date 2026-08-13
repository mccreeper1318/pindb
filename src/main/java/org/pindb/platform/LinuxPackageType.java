package org.pindb.platform;

import java.util.Locale;

public enum LinuxPackageType {
    DEB(".deb", "deb"),
    RPM(".rpm", "rpm");

    private final String extension;
    private final String scriptValue;

    LinuxPackageType(String extension, String scriptValue) {
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
}
