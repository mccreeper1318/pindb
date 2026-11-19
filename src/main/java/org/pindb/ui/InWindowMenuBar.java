package org.pindb.ui;

import javafx.beans.binding.Bindings;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A menu bar whose drop-downs remain inside the application scene.
 *
 * <p>JavaFX's Linux menu controls normally place their drop-downs in native
 * popup windows. Some Wayland/KDE configurations report incorrect screen
 * coordinates for those windows. Keeping the drop-down in the scene avoids
 * that platform coordinate conversion entirely while preserving normal menu
 * keyboard and accessibility behavior.</p>
 */
public final class InWindowMenuBar {
    private static final PseudoClass SHOWING = PseudoClass.getPseudoClass("showing");

    private final HBox bar = new HBox();
    private final Pane overlay = new Pane();
    private final List<Menu> menus;
    private final Map<Menu, Button> buttons = new LinkedHashMap<>();
    private final Map<Character, Menu> menuMnemonics = new LinkedHashMap<>();
    private final List<MenuLevel> levels = new ArrayList<>();
    private Menu activeMenu;

    public InWindowMenuBar(Menu... menus) {
        this.menus = List.of(menus);
        bar.getStyleClass().add("in-window-menu-bar");
        bar.setAccessibleRole(AccessibleRole.MENU_BAR);
        bar.setAccessibleText("Application menu");

        overlay.getStyleClass().add("in-window-menu-overlay");
        overlay.setPickOnBounds(false);

        for (Menu menu : this.menus) {
            Button button = new Button(displayText(menu.getText()));
            button.getStyleClass().add("in-window-menu-title");
            button.setFocusTraversable(true);
            button.setAccessibleRole(AccessibleRole.MENU);
            button.setAccessibleText(displayText(menu.getText()));
            button.setAccessibleHelp("Press Enter or Down Arrow to open this menu.");
            button.setOnAction(event -> {
                if (activeMenu == menu) {
                    hide(true);
                } else {
                    show(menu, true);
                }
            });
            button.setOnMouseEntered(event -> {
                if (activeMenu != null && activeMenu != menu) {
                    show(menu, false);
                }
            });
            button.setOnKeyPressed(event -> handleMenuButtonKey(menu, event));
            buttons.put(menu, button);
            bar.getChildren().add(button);

            Character mnemonic = mnemonicKey(menu.getText(), true);
            if (mnemonic != null) {
                menuMnemonics.putIfAbsent(mnemonic, menu);
            }
        }
    }

    public Node bar() {
        return bar;
    }

    public Pane overlay() {
        return overlay;
    }

    public void attach(StackPane host, Scene scene) {
        installAccelerators(scene);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, this::handleSceneKey);
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (activeMenu == null || !(event.getTarget() instanceof Node target)) {
                return;
            }
            if (!isDescendant(target, bar) && !isDescendant(target, overlay)) {
                hide(false);
            }
        });
        host.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene == null) {
                hide(false);
            }
        });
    }

    private void handleSceneKey(KeyEvent event) {
        if (event.isAltDown() && event.getCode() != KeyCode.ALT) {
            Character key = keyCharacter(event.getCode());
            Menu menu = key == null ? null : menuMnemonics.get(key);
            if (menu != null) {
                show(menu, true);
                event.consume();
                return;
            }
        }

        if (event.getCode() == KeyCode.ALT && activeMenu == null) {
            focusMenuButton(0);
            event.consume();
            return;
        }

        if (activeMenu == null) {
            return;
        }

        if (event.getCode() == KeyCode.ALT) {
            Button activeButton = buttons.get(activeMenu);
            hide(false);
            activeButton.requestFocus();
            event.consume();
            return;
        }

        switch (event.getCode()) {
            case ESCAPE -> {
                if (levels.size() > 1) {
                    closeDeepestLevel(true);
                } else {
                    Button activeButton = buttons.get(activeMenu);
                    hide(false);
                    activeButton.requestFocus();
                }
                event.consume();
            }
            case LEFT -> {
                if (levels.size() > 1) {
                    closeDeepestLevel(true);
                } else {
                    switchTopLevel(-1);
                }
                event.consume();
            }
            case RIGHT -> {
                ItemButton focused = focusedItem();
                if (focused != null && focused.item() instanceof Menu submenu && !submenu.isDisable()) {
                    openSubmenu(submenu, focused.button(), true);
                } else if (levels.size() == 1) {
                    switchTopLevel(1);
                }
                event.consume();
            }
            case DOWN -> {
                moveItemFocus(1);
                event.consume();
            }
            case UP -> {
                moveItemFocus(-1);
                event.consume();
            }
            case HOME -> {
                focusBoundaryItem(true);
                event.consume();
            }
            case END -> {
                focusBoundaryItem(false);
                event.consume();
            }
            case ENTER, SPACE -> {
                ItemButton focused = focusedItem();
                if (focused != null) {
                    activate(focused);
                    event.consume();
                }
            }
            default -> {
                Character mnemonic = keyCharacter(event.getCode());
                if (mnemonic != null && !event.isControlDown() && !event.isMetaDown()) {
                    ItemButton item = itemForMnemonic(currentLevel(), mnemonic);
                    if (item != null) {
                        activate(item);
                        event.consume();
                    }
                }
            }
        }
    }

    private void handleMenuButtonKey(Menu menu, KeyEvent event) {
        int index = menus.indexOf(menu);
        switch (event.getCode()) {
            case LEFT -> {
                focusMenuButton(wrap(index - 1, menus.size()));
                event.consume();
            }
            case RIGHT -> {
                focusMenuButton(wrap(index + 1, menus.size()));
                event.consume();
            }
            case DOWN, ENTER, SPACE -> {
                show(menu, true);
                event.consume();
            }
            case ESCAPE -> {
                hide(false);
                event.consume();
            }
            default -> {
            }
        }
    }

    private void show(Menu menu, boolean focusItems) {
        hide(false);
        activeMenu = menu;
        Button menuButton = buttons.get(menu);
        menuButton.pseudoClassStateChanged(SHOWING, true);

        Bounds buttonBounds = menuButton.localToScene(menuButton.getBoundsInLocal());
        javafx.geometry.Point2D position = overlay.sceneToLocal(buttonBounds.getMinX(), buttonBounds.getMaxY());
        MenuLevel level = createLevel(menu, null);
        level.box().relocate(Math.max(0, position.getX()), Math.max(0, position.getY()));
        levels.add(level);
        overlay.getChildren().setAll(level.box());
        overlay.applyCss();
        overlay.layout();

        if (focusItems) {
            focusFirstEnabled(level);
        }
    }

    private MenuLevel createLevel(Menu menu, Button parentButton) {
        VBox box = new VBox(1);
        box.getStyleClass().add("in-window-menu-popup");
        box.setAccessibleRole(AccessibleRole.MENU);
        box.setAccessibleText(displayText(menu.getText()));

        List<ItemButton> itemButtons = new ArrayList<>();
        for (MenuItem item : menu.getItems()) {
            if (item instanceof SeparatorMenuItem) {
                Separator separator = new Separator();
                separator.getStyleClass().add("in-window-menu-separator");
                separator.setFocusTraversable(false);
                box.getChildren().add(separator);
            } else {
                ItemButton itemButton = itemButton(item);
                itemButtons.add(itemButton);
                box.getChildren().add(itemButton.button());
            }
        }
        return new MenuLevel(menu, box, parentButton, itemButtons);
    }

    private ItemButton itemButton(MenuItem item) {
        Label marker = new Label();
        marker.getStyleClass().add("in-window-menu-marker");
        if (item instanceof RadioMenuItem radio) {
            marker.textProperty().bind(Bindings.when(radio.selectedProperty()).then("●").otherwise(""));
        } else if (item instanceof CheckMenuItem check) {
            marker.textProperty().bind(Bindings.when(check.selectedProperty()).then("✓").otherwise(""));
        }

        Label text = new Label(displayText(item.getText()));
        text.getStyleClass().add("in-window-menu-item-text");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label accelerator = new Label(acceleratorText(item.getAccelerator()));
        accelerator.getStyleClass().add("in-window-menu-accelerator");
        Label submenuArrow = new Label(item instanceof Menu ? "▶" : "");
        submenuArrow.getStyleClass().add("in-window-menu-accelerator");
        HBox content = new HBox(8, marker, text, spacer, accelerator, submenuArrow);
        content.setAlignment(Pos.CENTER_LEFT);

        Button button = new Button();
        button.getStyleClass().add("in-window-menu-item");
        button.setGraphic(content);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setFocusTraversable(true);
        button.disableProperty().bind(item.disableProperty());
        button.setAccessibleRole(item instanceof Menu ? AccessibleRole.MENU : AccessibleRole.MENU_ITEM);
        button.setAccessibleText(displayText(item.getText()));
        if (item.getAccelerator() != null) {
            button.setAccessibleHelp("Keyboard shortcut " + item.getAccelerator().getDisplayText());
        } else if (item instanceof Menu) {
            button.setAccessibleHelp("Submenu. Press Right Arrow or Enter to open.");
        }

        ItemButton result = new ItemButton(item, button);
        button.setOnAction(event -> activate(result));
        button.setOnMouseEntered(event -> {
            button.requestFocus();
            if (item instanceof Menu submenu && !submenu.isDisable()) {
                openSubmenu(submenu, button, false);
            } else {
                closeLevelsAfter(levelContaining(button));
            }
        });
        return result;
    }

    private void activate(ItemButton itemButton) {
        MenuItem item = itemButton.item();
        if (item.isDisable()) {
            return;
        }
        if (item instanceof Menu submenu) {
            openSubmenu(submenu, itemButton.button(), true);
            return;
        }
        hide(false);
        item.fire();
    }

    private void openSubmenu(Menu submenu, Button parentButton, boolean focusItems) {
        MenuLevel parentLevel = levelContaining(parentButton);
        if (parentLevel == null) {
            return;
        }
        closeLevelsAfter(parentLevel);

        Bounds parentBounds = parentButton.localToScene(parentButton.getBoundsInLocal());
        javafx.geometry.Point2D position = overlay.sceneToLocal(parentBounds.getMaxX(), parentBounds.getMinY());
        MenuLevel submenuLevel = createLevel(submenu, parentButton);
        submenuLevel.box().relocate(Math.max(0, position.getX()), Math.max(0, position.getY()));
        levels.add(submenuLevel);
        overlay.getChildren().add(submenuLevel.box());
        overlay.applyCss();
        overlay.layout();

        if (focusItems) {
            focusFirstEnabled(submenuLevel);
        }
    }

    private void moveItemFocus(int direction) {
        MenuLevel level = currentLevel();
        if (level == null || level.items().isEmpty()) {
            return;
        }
        int current = focusedIndex(level);
        int next = nextNavigableIndex(level.items().stream().map(ItemButton::item).toList(), current, direction);
        if (next >= 0) {
            level.items().get(next).button().requestFocus();
        }
    }

    private void focusBoundaryItem(boolean first) {
        MenuLevel level = currentLevel();
        if (level == null) {
            return;
        }
        List<MenuItem> items = level.items().stream().map(ItemButton::item).toList();
        int index = boundaryNavigableIndex(items, first);
        if (index >= 0) {
            level.items().get(index).button().requestFocus();
        }
    }

    private void focusFirstEnabled(MenuLevel level) {
        int index = boundaryNavigableIndex(level.items().stream().map(ItemButton::item).toList(), true);
        if (index >= 0) {
            level.items().get(index).button().requestFocus();
        } else if (activeMenu != null) {
            buttons.get(activeMenu).requestFocus();
        }
    }

    private ItemButton focusedItem() {
        for (int levelIndex = levels.size() - 1; levelIndex >= 0; levelIndex--) {
            for (ItemButton item : levels.get(levelIndex).items()) {
                if (item.button().isFocused()) {
                    return item;
                }
            }
        }
        return null;
    }

    private int focusedIndex(MenuLevel level) {
        for (int index = 0; index < level.items().size(); index++) {
            if (level.items().get(index).button().isFocused()) {
                return index;
            }
        }
        return -1;
    }

    private ItemButton itemForMnemonic(MenuLevel level, char key) {
        if (level == null) {
            return null;
        }
        for (ItemButton itemButton : level.items()) {
            if (itemButton.item().isDisable()) {
                continue;
            }
            Character mnemonic = mnemonicKey(itemButton.item().getText(), true);
            if (mnemonic != null && mnemonic == Character.toUpperCase(key)) {
                return itemButton;
            }
        }
        return null;
    }

    private void switchTopLevel(int direction) {
        if (activeMenu == null || menus.isEmpty()) {
            return;
        }
        int current = menus.indexOf(activeMenu);
        Menu next = menus.get(wrap(current + direction, menus.size()));
        show(next, true);
    }

    private void focusMenuButton(int index) {
        if (menus.isEmpty()) {
            return;
        }
        buttons.get(menus.get(wrap(index, menus.size()))).requestFocus();
    }

    private void closeDeepestLevel(boolean restoreFocus) {
        if (levels.size() <= 1) {
            return;
        }
        MenuLevel removed = levels.removeLast();
        overlay.getChildren().remove(removed.box());
        if (restoreFocus && removed.parentButton() != null) {
            removed.parentButton().requestFocus();
        }
    }

    private void closeLevelsAfter(MenuLevel level) {
        int index = levels.indexOf(level);
        while (levels.size() > index + 1) {
            MenuLevel removed = levels.removeLast();
            overlay.getChildren().remove(removed.box());
        }
    }

    private MenuLevel levelContaining(Button button) {
        for (MenuLevel level : levels) {
            if (level.items().stream().anyMatch(item -> item.button() == button)) {
                return level;
            }
        }
        return null;
    }

    private MenuLevel currentLevel() {
        return levels.isEmpty() ? null : levels.getLast();
    }

    private void installAccelerators(Scene scene) {
        for (Menu menu : menus) {
            installAccelerators(scene, menu);
        }
    }

    private void installAccelerators(Scene scene, Menu menu) {
        for (MenuItem item : menu.getItems()) {
            if (item instanceof Menu submenu) {
                installAccelerators(scene, submenu);
            }
            KeyCombination accelerator = item.getAccelerator();
            if (accelerator != null) {
                scene.getAccelerators().put(accelerator, () -> {
                    if (!item.isDisable()) {
                        item.fire();
                    }
                });
            }
        }
    }

    private void hide(boolean restoreFocus) {
        Button restore = activeMenu == null ? null : buttons.get(activeMenu);
        if (activeMenu != null) {
            restore.pseudoClassStateChanged(SHOWING, false);
        }
        activeMenu = null;
        levels.clear();
        overlay.getChildren().clear();
        if (restoreFocus && restore != null) {
            restore.requestFocus();
        }
    }

    static int nextNavigableIndex(List<MenuItem> items, int current, int direction) {
        if (items == null || items.isEmpty() || direction == 0) {
            return -1;
        }
        int step = direction > 0 ? 1 : -1;
        int index = current;
        for (int attempts = 0; attempts < items.size(); attempts++) {
            index = wrap(index + step, items.size());
            MenuItem item = items.get(index);
            if (!(item instanceof SeparatorMenuItem) && !item.isDisable()) {
                return index;
            }
        }
        return -1;
    }

    static int boundaryNavigableIndex(List<MenuItem> items, boolean first) {
        if (items == null || items.isEmpty()) {
            return -1;
        }
        int index = first ? 0 : items.size() - 1;
        int step = first ? 1 : -1;
        while (index >= 0 && index < items.size()) {
            MenuItem item = items.get(index);
            if (!(item instanceof SeparatorMenuItem) && !item.isDisable()) {
                return index;
            }
            index += step;
        }
        return -1;
    }

    static Character mnemonicKey(String text, boolean fallbackToFirstCharacter) {
        if (text == null || text.isBlank()) {
            return null;
        }
        for (int index = 0; index < text.length() - 1; index++) {
            if (text.charAt(index) == '_' && text.charAt(index + 1) != '_') {
                return Character.toUpperCase(text.charAt(index + 1));
            }
            if (text.charAt(index) == '_' && text.charAt(index + 1) == '_') {
                index++;
            }
        }
        if (!fallbackToFirstCharacter) {
            return null;
        }
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (Character.isLetterOrDigit(current)) {
                return Character.toUpperCase(current);
            }
        }
        return null;
    }

    static String displayText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (current == '_' && index + 1 < text.length()) {
                if (text.charAt(index + 1) == '_') {
                    result.append('_');
                    index++;
                }
                continue;
            }
            result.append(current);
        }
        return result.toString();
    }

    private static Character keyCharacter(KeyCode code) {
        if (code == null) {
            return null;
        }
        String name = code.getName();
        if (name == null || name.length() != 1) {
            return null;
        }
        char value = name.charAt(0);
        return Character.isLetterOrDigit(value) ? Character.toUpperCase(value) : null;
    }

    private static int wrap(int index, int size) {
        if (size <= 0) {
            return -1;
        }
        int result = index % size;
        return result < 0 ? result + size : result;
    }

    private static boolean isDescendant(Node node, Node ancestor) {
        if (ancestor == null) {
            return false;
        }
        for (Node current = node; current != null; current = current.getParent()) {
            if (current == ancestor) {
                return true;
            }
        }
        return false;
    }

    private static String acceleratorText(KeyCombination accelerator) {
        return accelerator == null ? "" : accelerator.getDisplayText();
    }

    private record ItemButton(MenuItem item, Button button) {
    }

    private record MenuLevel(Menu menu, VBox box, Button parentButton, List<ItemButton> items) {
        private MenuLevel {
            items = List.copyOf(items);
        }
    }
}
