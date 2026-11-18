package org.pindb;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AppContextUpdateArchitectureTest {
    @Test
    void unsupportedArchitectureMessageExplainsAutomaticUpdatesAreUnavailable() {
        String previous = System.getProperty("os.arch");
        try {
            System.setProperty("os.arch", "mystery-cpu");
            String message = AppContext.unsupportedArchitectureMessage();

            assertTrue(message.contains("cannot automatically select an update package"));
            assertTrue(message.contains("mystery-cpu"));
            assertTrue(message.contains("GitHub Releases"));
        } finally {
            if (previous == null) {
                System.clearProperty("os.arch");
            } else {
                System.setProperty("os.arch", previous);
            }
        }
    }
}
