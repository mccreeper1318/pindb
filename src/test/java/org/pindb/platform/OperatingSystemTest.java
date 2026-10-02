package org.pindb.platform;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OperatingSystemTest {
    @Test
    void detectsWindows() {
        assertEquals(OperatingSystem.WINDOWS, OperatingSystem.fromOsName("Windows 11"));
    }

    @Test
    void detectsMacOs() {
        assertEquals(OperatingSystem.MACOS, OperatingSystem.fromOsName("Mac OS X"));
    }

    @Test
    void detectsDarwinAsMacOsRatherThanWindows() {
        assertEquals(OperatingSystem.MACOS, OperatingSystem.fromOsName("Darwin"));
    }

    @Test
    void detectsLinux() {
        assertEquals(OperatingSystem.LINUX, OperatingSystem.fromOsName("Linux"));
    }

    @Test
    void treatsUnknownSystemsAsOther() {
        assertEquals(OperatingSystem.OTHER, OperatingSystem.fromOsName("FreeBSD"));
        assertEquals(OperatingSystem.OTHER, OperatingSystem.fromOsName(null));
    }
}
