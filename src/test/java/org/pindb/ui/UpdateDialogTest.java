package org.pindb.ui;

import org.junit.jupiter.api.Test;
import org.pindb.platform.LinuxDistribution;
import org.pindb.platform.NativePackageType;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UpdateDialogTest {
    @Test
    void macUpdateGuidanceExplainsProtectedStagingAndManualReopen() {
        LinuxDistribution mac = LinuxDistribution.detect("Mac OS X", "");

        assertEquals(
                "PinDB will securely stage the checksum-verified .pkg package with administrator approval, "
                        + "open it in macOS Installer, then close. Complete the installation in Installer and reopen PinDB when it finishes.",
                UpdateDialog.updateMessage(NativePackageType.MACOS_PKG, mac));
    }

    @Test
    void nonMacUpdateGuidanceKeepsAutomaticRestartMessage() {
        LinuxDistribution windows = LinuxDistribution.detect("Windows 11", "");

        assertEquals(
                "PinDB will install the matching .exe package after administrator approval, then close and reopen.",
                UpdateDialog.updateMessage(NativePackageType.WINDOWS_EXE, windows));
    }
}
