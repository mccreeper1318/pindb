package org.pindb.ui;

import javafx.beans.binding.Bindings;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A menu bar whose drop-downs remain inside the application scene.
 *
 * <p>JavaFX's Linux menu controls normally place their drop-downs in native
 * popup windows. Some Wayland/KDE configurations report incorrect screen
 * coordinates for those windows. Keeping the drop-down in the scene avoids
 * that platform coordinate conversion entirely.</p>
 */
public final class InWindowMenuBar {
    private static final PseudoClass SHOWING = PseudoClass.getPseudoClass("showing");

    private final HBox bar = new HBox();
    private final Pane overlay = new Pane();
    private final List<Menu> menus;
    private final Map<Menu, Button> buttons = new LinkedHashMap<>();
    private Menu activeMenu;
    private VBox dropDown;

    public InWindowMenuBar(Menu... menus) {
        this.menus = List.of(menus);
        bar.getStyleClass().add("in-window-menu-bar");
        overlay.getStyleClass().add("in-window-menu-overlay");
        overlay.setPickOnBounds(false);

        for (Menu menu : this.menus) {
            Button button = new Button(menu.getText());
            button.getStyleClass().add("in-window-menu-title");
            button.setMnemonicParsing(true);
            button.setOnAction(event -> {
                if (activeMenu == menu) {
                    hide();
                } else {
                    show(menu);
                }
            });
            button.setOnMouseEntered(event -> {
                if (activeMenu != null && activeMenu != menu) {
                    show(menu);
                }
            });
            buttons.put(menu, button);
            bar.getChildren().add(button);
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
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE && activeMenu != null) {
                hide();
                event.consume();
            }
        });
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (activeMenu == null || !(event.getTarget() instanceof Node target)) {
                return;
            }
            if (!isDescendant(target, bar) && !isDescendant(target, dropDown)) {
                hide();
            }
        });
        host.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene == null) {
                hide();
            }
        });
    }

    private void show(Menu menu) {
        hide();
        activeMenu = menu;
        Button menuButton = buttons.get(menu);
        menuButton.pseudoClassStateChanged(SHOWING, true);

        dropDown = new VBox(1);
        dropDown.getStyleClass().add("in-window-menu-popup");
        for (MenuItem item : menu.getItems()) {
            if (item instanceof SeparatorMenuItem) {
                Separator separator = new Separator();
                separator.getStyleClass().add("in-window-menu-separator");
                dropDown.getChildren().add(separator);
            } else {
                dropDown.getChildren().add(itemButton(item));
            }
        }
        overlay.getChildren().setAll(dropDown);
        overlay.applyCss();
        overlay.layout();

        Bounds buttonBounds = menuButton.localToScene(menuButton.getBoundsInLocal());
        javafx.geometry.Point2D position = overlay.sceneToLocal(buttonBounds.getMinX(), buttonBounds.getMaxY());
        dropDown.relocate(Math.max(0, position.getX()), Math.max(0, position.getY()));
    }

    private Button itemButton(MenuItem item) {
        Label marker = new Label();
        marker.getStyleClass().add("in-window-menu-marker");
        if (item instanceof RadioMenuItem radio) {
            marker.textProperty().bind(Bindings.when(radio.selectedProperty()).then("●").otherwise(""));
        }

        Label text = new Label(item.getText());
        text.getStyleClass().add("in-window-menu-item-text");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label accelerator = new Label(acceleratorText(item.getAccelerator()));
        accelerator.getStyleClass().add("in-window-menu-accelerator");
        HBox content = new HBox(8, marker, text, spacer, accelerator);
        content.setAlignment(Pos.CENTER_LEFT);

        Button button = new Button();
        button.getStyleClass().add("in-window-menu-item");
        button.setGraphic(content);
        button.setMaxWidth(Double.MAX_VALUE);
        button.disableProperty().bind(item.disableProperty());
        button.setOnAction(event -> {
            hide();
            item.fire();
        });
        return button;
    }

    private void installAccelerators(Scene scene) {
        for (Menu menu : menus) {
            for (MenuItem item : menu.getItems()) {
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
    }

    private void hide() {
        if (activeMenu != null) {
            buttons.get(activeMenu).pseudoClassStateChanged(SHOWING, false);
        }
        activeMenu = null;
        dropDown = null;
        overlay.getChildren().clear();
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
}
