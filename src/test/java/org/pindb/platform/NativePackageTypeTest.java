package org.pindb.platform;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativePackageTypeTest {
    @Test
    void recognizesAllSupportedNativePackageExtensions() {
        assertEquals(NativePackageType.DEB, NativePackageType.fromFileName("pindb.deb").orElseThrow());
        assertEquals(NativePackageType.RPM, NativePackageType.fromFileName("pindb.rpm").orElseThrow());
        assertEquals(NativePackageType.WINDOWS_EXE, NativePackageType.fromFileName("PinDB.exe").orElseThrow());
        assertEquals(NativePackageType.MACOS_PKG, NativePackageType.fromFileName("PinDB.pkg").orElseThrow());
    }

    @Test
    void rejectsUnknownPackageExtensions() {
        assertTrue(NativePackageType.fromFileName("PinDB.zip").isEmpty());
        assertTrue(NativePackageType.fromFileName(null).isEmpty());
    }
}
