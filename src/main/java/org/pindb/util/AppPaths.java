package org.pindb.util;

import org.pindb.platform.OperatingSystem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public final class AppPaths {
    private AppPaths() {
    }

    public static Path configDirectory() {
        return ensure(configDirectory(
                OperatingSystem.current(),
                homeDirectory(),
                System.getenv("APPDATA"),
                System.getenv("XDG_CONFIG_HOME")));
    }

    public static Path stateDirectory() {
        return ensure(stateDirectory(
                OperatingSystem.current(),
                homeDirectory(),
                System.getenv("LOCALAPPDATA"),
                System.getenv("XDG_STATE_HOME")));
    }

    public static Path cacheDirectory() {
        return ensure(cacheDirectory(
                OperatingSystem.current(),
                homeDirectory(),
                System.getenv("LOCALAPPDATA"),
                System.getenv("XDG_CACHE_HOME")));
    }

    public static Optional<Path> legacyMacConfigDirectory() {
        if (OperatingSystem.current() != OperatingSystem.MACOS) {
            return Optional.empty();
        }
        return Optional.of(legacyMacConfigDirectory(homeDirectory(), System.getenv("XDG_CONFIG_HOME")));
    }

    static Path configDirectory(OperatingSystem operatingSystem, Path home, String appData, String xdgConfigHome) {
        return switch (operatingSystem) {
            case WINDOWS -> windowsRoamingBase(home, appData).resolve("PinDB");
            case MACOS -> macApplicationSupportBase(home).resolve("PinDB");
            case LINUX, OTHER -> xdgBase(home.resolve(".config"), xdgConfigHome).resolve("pindb");
        };
    }

    static Path stateDirectory(OperatingSystem operatingSystem, Path home, String localAppData, String xdgStateHome) {
        return switch (operatingSystem) {
            case WINDOWS -> windowsLocalBase(home, localAppData).resolve("PinDB").resolve("State");
            case MACOS -> macApplicationSupportBase(home).resolve("PinDB").resolve("State");
            case LINUX, OTHER -> xdgBase(home.resolve(".local").resolve("state"), xdgStateHome).resolve("pindb");
        };
    }

    static Path cacheDirectory(OperatingSystem operatingSystem, Path home, String localAppData, String xdgCacheHome) {
        return switch (operatingSystem) {
            case WINDOWS -> windowsLocalBase(home, localAppData).resolve("PinDB").resolve("Cache");
            case MACOS -> home.resolve("Library").resolve("Caches").resolve("PinDB");
            case LINUX, OTHER -> xdgBase(home.resolve(".cache"), xdgCacheHome).resolve("pindb");
        };
    }

    static Path legacyMacConfigDirectory(Path home, String xdgConfigHome) {
        return xdgBase(home.resolve(".config"), xdgConfigHome).resolve("pindb");
    }

    private static Path homeDirectory() {
        return Paths.get(System.getProperty("user.home"));
    }

    private static Path macApplicationSupportBase(Path home) {
        return home.resolve("Library").resolve("Application Support");
    }

    private static Path windowsRoamingBase(Path home, String appData) {
        return appData == null || appData.isBlank()
                ? home.resolve("AppData").resolve("Roaming")
                : Paths.get(appData);
    }

    private static Path windowsLocalBase(Path home, String localAppData) {
        return localAppData == null || localAppData.isBlank()
                ? home.resolve("AppData").resolve("Local")
                : Paths.get(localAppData);
    }

    private static Path xdgBase(Path fallback, String configured) {
        return configured == null || configured.isBlank() ? fallback : Paths.get(configured);
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
