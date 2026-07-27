package org.pindb.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class AppPaths {
    private AppPaths() {
    }

    public static Path configDirectory() {
        String xdg = System.getenv("XDG_CONFIG_HOME");
        Path base = xdg == null || xdg.isBlank()
                ? Paths.get(System.getProperty("user.home"), ".config")
                : Paths.get(xdg);
        return ensure(base.resolve("pindb"));
    }

    public static Path stateDirectory() {
        String xdg = System.getenv("XDG_STATE_HOME");
        Path base = xdg == null || xdg.isBlank()
                ? Paths.get(System.getProperty("user.home"), ".local", "state")
                : Paths.get(xdg);
        return ensure(base.resolve("pindb"));
    }

    public static Path cacheDirectory() {
        String xdg = System.getenv("XDG_CACHE_HOME");
        Path base = xdg == null || xdg.isBlank()
                ? Paths.get(System.getProperty("user.home"), ".cache")
                : Paths.get(xdg);
        return ensure(base.resolve("pindb"));
    }

    public static Path ensure(Path directory) {
        try {
            Files.createDirectories(directory);
            return directory;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create application directory: " + directory, exception);
        }
    }
}
