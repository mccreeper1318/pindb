package org.pindb;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PinDBLauncherTest {
    @Test
    void recognizesPostUpdateLaunch() {
        assertTrue(PinDBLauncher.isPostUpdateLaunch(new String[]{
                "--updated-tag=0.2.1-beta.4",
                "--updated-notes=/tmp/release-notes.md"
        }));
    }

    @Test
    void ignoresNormalLaunch() {
        assertFalse(PinDBLauncher.isPostUpdateLaunch(new String[]{"/tmp/example.pindb"}));
    }
}
