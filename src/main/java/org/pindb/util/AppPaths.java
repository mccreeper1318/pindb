package org.pindb.util;

import org.pindb.platform.OperatingSystem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class AppPaths {
    private AppPaths() {
    }

    public static Path configDirectory() {
        if (OperatingSystem.current() == OperatingSystem.WINDOWS) {
            return ensure(windowsRoamingBase().resolve("PinDB"));
        }
        String xdg = System.getenv("XDG_CONFIG_HOME");
        Path base = xdg == null || xdg.isBlank()
                ? Paths.get(System.getProperty("user.home"), ".config")
                : Paths.get(xdg);
        return ensure(base.resolve("pindb"));
    }

    public static Path stateDirectory() {
        if (OperatingSystem.current() == OperatingSystem.WINDOWS) {
            return ensure(windowsLocalBase().resolve("PinDB").resolve("State"));
        }
        String xdg = System.getenv("XDG_STATE_HOME");
        Path base = xdg == null || xdg.isBlank()
                ? Paths.get(System.getProperty("user.home"), ".local", "state")
                : Paths.get(xdg);
        return ensure(base.resolve("pindb"));
    }

    public static Path cacheDirectory() {
        if (OperatingSystem.current() == OperatingSystem.WINDOWS) {
            return ensure(windowsLocalBase().resolve("PinDB").resolve("Cache"));
        }
        String xdg = System.getenv("XDG_CACHE_HOME");
        Path base = xdg == null || xdg.isBlank()
                ? Paths.get(System.getProperty("user.home"), ".cache")
                : Paths.get(xdg);
        return ensure(base.resolve("pindb"));
    }

    private static Path windowsRoamingBase() {
        String appData = System.getenv("APPDATA");
        return appData == null || appData.isBlank()
                ? Paths.get(System.getProperty("user.home"), "AppData", "Roaming")
                : Paths.get(appData);
    }

    private static Path windowsLocalBase() {
        String localAppData = System.getenv("LOCALAPPDATA");
        return localAppData == null || localAppData.isBlank()
                ? Paths.get(System.getProperty("user.home"), "AppData", "Local")
                : Paths.get(localAppData);
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
