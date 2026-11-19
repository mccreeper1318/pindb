package org.pindb.ui;

import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
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
        MenuItem first = new MenuItem("First");
        MenuItem disabled = new MenuItem("Disabled");
        disabled.setDisable(true);
        SeparatorMenuItem separator = new SeparatorMenuItem();
        MenuItem last = new MenuItem("Last");
        List<MenuItem> items = List.of(first, disabled, separator, last);

        assertEquals(3, InWindowMenuBar.nextNavigableIndex(items, 0, 1));
        assertEquals(0, InWindowMenuBar.nextNavigableIndex(items, 3, 1));
        assertEquals(3, InWindowMenuBar.nextNavigableIndex(items, 0, -1));
        assertEquals(0, InWindowMenuBar.boundaryNavigableIndex(items, true));
        assertEquals(3, InWindowMenuBar.boundaryNavigableIndex(items, false));
    }

    @Test
    void navigationReportsNoTargetWhenEveryItemIsUnavailable() {
        MenuItem disabled = new MenuItem("Disabled");
        disabled.setDisable(true);

        assertEquals(-1, InWindowMenuBar.nextNavigableIndex(
                List.of(disabled, new SeparatorMenuItem()), -1, 1));
        assertEquals(-1, InWindowMenuBar.boundaryNavigableIndex(
                List.of(disabled, new SeparatorMenuItem()), true));
    }
}
