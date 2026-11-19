package org.pindb.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class InWindowMenuBarPolicyTest {

    @Test
    void derivesTopLevelMnemonicFromExplicitMarkerOrFirstCharacter() {
        assertEquals('F', InWindowMenuBar.mnemonicKey("_File", true));
        assertEquals('D', InWindowMenuBar.mnemonicKey("Database", true));
        assertNull(InWindowMenuBar.mnemonicKey("Database", false));
    }

    @Test
    void removesMnemonicMarkersWithoutChangingEscapedUnderscores() {
        assertEquals("File", InWindowMenuBar.displayText("_File"));
        assertEquals("Save_As", InWindowMenuBar.displayText("Save__As"));
    }

    @Test
    void keyboardNavigationSkipsSeparatorsAndDisabledItemsAndWraps() {
        List<Boolean> navigable = List.of(true, false, false, true);

        assertEquals(3, InWindowMenuBar.nextNavigableIndex(navigable, 0, 1));
        assertEquals(0, InWindowMenuBar.nextNavigableIndex(navigable, 3, 1));
        assertEquals(3, InWindowMenuBar.nextNavigableIndex(navigable, 0, -1));
        assertEquals(0, InWindowMenuBar.nextNavigableIndex(navigable, -1, 1));
        assertEquals(3, InWindowMenuBar.nextNavigableIndex(navigable, -1, -1));
        assertEquals(0, InWindowMenuBar.boundaryNavigableIndex(navigable, true));
        assertEquals(3, InWindowMenuBar.boundaryNavigableIndex(navigable, false));
    }

    @Test
    void navigationReportsNoTargetWhenEveryItemIsUnavailable() {
        List<Boolean> navigable = List.of(false, false);

        assertEquals(-1, InWindowMenuBar.nextNavigableIndex(navigable, -1, 1));
        assertEquals(-1, InWindowMenuBar.boundaryNavigableIndex(navigable, true));
    }
}
