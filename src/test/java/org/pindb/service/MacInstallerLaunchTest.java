package org.pindb.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MacInstallerLaunchTest {
    @Test
    void acceptsSuccessfulOpenExitStatus() {
        assertDoesNotThrow(() -> UpdateInstaller.requireSuccessfulMacInstallerLaunch(0, ""));
    }

    @Test
    void rejectsFailedOpenExitStatusWithOutput() {
        IOException failure = assertThrows(IOException.class,
                () -> UpdateInstaller.requireSuccessfulMacInstallerLaunch(1, "LaunchServices failed"));

        assertTrue(failure.getMessage().contains("status 1"));
        assertTrue(failure.getMessage().contains("LaunchServices failed"));
    }

    @Test
    void rejectsFailedOpenExitStatusWithoutOutput() {
        IOException failure = assertThrows(IOException.class,
                () -> UpdateInstaller.requireSuccessfulMacInstallerLaunch(2, "  "));

        assertTrue(failure.getMessage().contains("status 2"));
        assertTrue(failure.getMessage().contains("No additional output"));
    }
}
