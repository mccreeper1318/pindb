package org.pindb;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class AppVersion {
    public static final String VERSION = loadVersion();

    private AppVersion() {
    }

    private static String loadVersion() {
        Properties properties = new Properties();
        try (InputStream input = AppVersion.class.getResourceAsStream("/org/pindb/app/version.properties")) {
            if (input != null) {
                properties.load(input);
                String version = properties.getProperty("version");
                if (version != null && !version.isBlank() && !version.contains("${")) {
                    return version.trim();
                }
            }
        } catch (IOException ignored) {
            // Fall through to the source-tree default.
        }
        return "0.1";
    }
}
