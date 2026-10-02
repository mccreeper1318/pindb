package org.pindb.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pindb.platform.OperatingSystem;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppPathsTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void macosUsesNativeLibraryDirectoriesAndIgnoresXdgOverrides() {
        Path home = temporaryDirectory.resolve("home");

        assertEquals(home.resolve("Library").resolve("Application Support").resolve("PinDB"),
                AppPaths.configDirectory(OperatingSystem.MACOS, home,
                        temporaryDirectory.resolve("appdata").toString(),
                        temporaryDirectory.resolve("xdg-config").toString()));
        assertEquals(home.resolve("Library").resolve("Application Support").resolve("PinDB").resolve("State"),
                AppPaths.stateDirectory(OperatingSystem.MACOS, home,
                        temporaryDirectory.resolve("localappdata").toString(),
                        temporaryDirectory.resolve("xdg-state").toString()));
        assertEquals(home.resolve("Library").resolve("Caches").resolve("PinDB"),
                AppPaths.cacheDirectory(OperatingSystem.MACOS, home,
                        temporaryDirectory.resolve("localappdata").toString(),
                        temporaryDirectory.resolve("xdg-cache").toString()));
    }

    @Test
    void windowsPathsRemainAppDataBased() {
        Path home = temporaryDirectory.resolve("home");
        Path roaming = temporaryDirectory.resolve("Roaming");
        Path local = temporaryDirectory.resolve("Local");

        assertEquals(roaming.resolve("PinDB"),
                AppPaths.configDirectory(OperatingSystem.WINDOWS, home, roaming.toString(), null));
        assertEquals(local.resolve("PinDB").resolve("State"),
                AppPaths.stateDirectory(OperatingSystem.WINDOWS, home, local.toString(), null));
        assertEquals(local.resolve("PinDB").resolve("Cache"),
                AppPaths.cacheDirectory(OperatingSystem.WINDOWS, home, local.toString(), null));
    }

    @Test
    void windowsFallsBackToStandardUserProfilePaths() {
        Path home = temporaryDirectory.resolve("home");

        assertEquals(home.resolve("AppData").resolve("Roaming").resolve("PinDB"),
                AppPaths.configDirectory(OperatingSystem.WINDOWS, home, "", null));
        assertEquals(home.resolve("AppData").resolve("Local").resolve("PinDB").resolve("State"),
                AppPaths.stateDirectory(OperatingSystem.WINDOWS, home, null, null));
        assertEquals(home.resolve("AppData").resolve("Local").resolve("PinDB").resolve("Cache"),
                AppPaths.cacheDirectory(OperatingSystem.WINDOWS, home, null, null));
    }

    @Test
    void linuxPathsContinueToHonorXdgOverrides() {
        Path home = temporaryDirectory.resolve("home");
        Path config = temporaryDirectory.resolve("xdg-config");
        Path state = temporaryDirectory.resolve("xdg-state");
        Path cache = temporaryDirectory.resolve("xdg-cache");

        assertEquals(config.resolve("pindb"),
                AppPaths.configDirectory(OperatingSystem.LINUX, home, null, config.toString()));
        assertEquals(state.resolve("pindb"),
                AppPaths.stateDirectory(OperatingSystem.LINUX, home, null, state.toString()));
        assertEquals(cache.resolve("pindb"),
                AppPaths.cacheDirectory(OperatingSystem.LINUX, home, null, cache.toString()));
    }

    @Test
    void linuxPathsKeepExistingHomeFallbacks() {
        Path home = temporaryDirectory.resolve("home");

        assertEquals(home.resolve(".config").resolve("pindb"),
                AppPaths.configDirectory(OperatingSystem.LINUX, home, null, null));
        assertEquals(home.resolve(".local").resolve("state").resolve("pindb"),
                AppPaths.stateDirectory(OperatingSystem.LINUX, home, null, ""));
        assertEquals(home.resolve(".cache").resolve("pindb"),
                AppPaths.cacheDirectory(OperatingSystem.LINUX, home, null, null));
    }

    @Test
    void ensureCreatesNestedApplicationDirectory() {
        Path directory = temporaryDirectory.resolve("Library")
                .resolve("Application Support").resolve("PinDB").resolve("State");

        assertEquals(directory, AppPaths.ensure(directory));
        assertTrue(Files.isDirectory(directory));
    }
}
