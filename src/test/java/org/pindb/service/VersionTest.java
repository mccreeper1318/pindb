package org.pindb.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VersionTest {
    @Test
    void acceptsSupportedTagPrefixes() {
        assertEquals("0.1", Version.parse("0.1").normalized());
        assertEquals("0.1", Version.parse("v0.1").normalized());
        assertEquals("0.1", Version.parse("v.0.1").normalized());
        assertEquals("1.3.65", Version.parse("1.3.65").normalized());
    }

    @Test
    void comparesNumericPartsNumerically() {
        assertTrue(Version.parse("0.10").compareTo(Version.parse("0.9")) > 0);
        assertEquals(0, Version.parse("1.2").compareTo(Version.parse("1.2.0")));
    }

    @Test
    void stableReleaseIsNewerThanPrerelease() {
        assertTrue(Version.parse("0.2").compareTo(Version.parse("0.2-beta.1")) > 0);
        assertTrue(Version.parse("0.2-beta.2").compareTo(Version.parse("0.2-beta.1")) > 0);
    }

    @Test
    void rejectsInvalidVersions() {
        assertThrows(IllegalArgumentException.class, () -> Version.parse("version 0.1"));
        assertThrows(IllegalArgumentException.class, () -> Version.parse("0"));
        assertThrows(IllegalArgumentException.class, () -> Version.parse("0.x"));
    }
}
