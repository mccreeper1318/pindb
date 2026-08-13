package org.pindb.platform;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SystemArchitectureTest {
    @Test
    void recognizesCommonRuntimeArchitectureNames() {
        assertEquals(SystemArchitecture.X86_64, SystemArchitecture.fromOsArch("amd64"));
        assertEquals(SystemArchitecture.X86_64, SystemArchitecture.fromOsArch("x86_64"));
        assertEquals(SystemArchitecture.AARCH64, SystemArchitecture.fromOsArch("aarch64"));
        assertEquals(SystemArchitecture.AARCH64, SystemArchitecture.fromOsArch("arm64"));
    }

    @Test
    void recognizesDebAndRpmAssetArchitectures() {
        assertEquals(SystemArchitecture.X86_64,
                SystemArchitecture.fromAssetName("pindb_0.2-1_amd64.deb"));
        assertEquals(SystemArchitecture.X86_64,
                SystemArchitecture.fromAssetName("pindb-0.2-1.x86_64.rpm"));
        assertEquals(SystemArchitecture.AARCH64,
                SystemArchitecture.fromAssetName("pindb-0.2-1.aarch64.rpm"));
    }
}
