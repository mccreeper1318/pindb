package org.pindb;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PinDBApplicationTest {
    @Test
    void matchesEquivalentReleaseTags() {
        assertTrue(PinDBApplication.sameVersion("0.2.1-beta.4", "v0.2.1-beta.4"));
    }

    @Test
    void rejectsDifferentReleaseTags() {
        assertFalse(PinDBApplication.sameVersion("0.2.1-beta.3", "0.2.1-beta.4"));
    }
}
