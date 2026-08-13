package org.pindb.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Properties;

public final class GitHubAppConfig {
    private static final String ENV_CLIENT_ID = "PINDB_GITHUB_CLIENT_ID";
    private static final String PROPERTY_CLIENT_ID = "pindb.github.clientId";

    private GitHubAppConfig() {
    }

    public static String clientId() {
        String system = System.getProperty(PROPERTY_CLIENT_ID, "").trim();
        if (!system.isBlank()) {
            return system;
        }
        String environment = Objects.requireNonNullElse(System.getenv(ENV_CLIENT_ID), "").trim();
        if (!environment.isBlank()) {
            return environment;
        }
        Properties properties = new Properties();
        try (InputStream input = GitHubAppConfig.class.getResourceAsStream(
                "/org/pindb/app/github-app.properties")) {
            if (input != null) {
                properties.load(input);
            }
        } catch (IOException ignored) {
            // The reporter will show a clear configuration message.
        }
        return properties.getProperty("clientId", "").trim();
    }

    public static boolean configured() {
        return !clientId().isBlank() && !clientId().contains("REPLACE");
    }
}
