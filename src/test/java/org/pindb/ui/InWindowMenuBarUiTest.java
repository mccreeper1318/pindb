package org.pindb.ui;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.AccessibleRole;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "PINDB_RUN_JAVAFX_TESTS", matches = "true")
class InWindowMenuBarUiTest {

    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        assertTrue(started.await(10, TimeUnit.SECONDS), "JavaFX toolkit did not start.");
    }

    @AfterAll
    static void stopJavaFx() {
        Platform.exit();
    }

    @Test
    void exposesMenuAccessibilityAndHandlesAltMnemonicAndTabDismissal() throws Exception {
        runOnFxThread(() -> {
            Menu file = new Menu("_File");
            file.getItems().add(new MenuItem("_New Entry"));
            InWindowMenuBar menuBar = new InWindowMenuBar(file);

            StackPane root = new StackPane();
            root.getChildren().addAll(menuBar.bar(), menuBar.overlay());
            Scene scene = new Scene(root, 640, 480);
            menuBar.attach(root, scene);

            Stage stage = new Stage();
            try {
                stage.setScene(scene);
                stage.show();

                assertEquals(AccessibleRole.MENU_BAR, menuBar.bar().getAccessibleRole());
                Button title = (Button) ((HBox) menuBar.bar()).getChildren().getFirst();
                assertEquals(AccessibleRole.MENU, title.getAccessibleRole());

                Event.fireEvent(root, new KeyEvent(
                        KeyEvent.KEY_PRESSED, "f", "f", KeyCode.F,
                        false, false, true, false));
                assertFalse(menuBar.overlay().getChildren().isEmpty(),
                        "Alt+F should open the File menu.");

                Event.fireEvent(root, new KeyEvent(
                        KeyEvent.KEY_PRESSED, "", "", KeyCode.TAB,
                        false, false, false, false));
                assertTrue(menuBar.overlay().getChildren().isEmpty(),
                        "Tab should dismiss the menu before focus leaves it.");
            } finally {
                stage.close();
            }
        });
    }

    private static void runOnFxThread(ThrowingRunnable action) throws Exception {
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                finished.countDown();
            }
        });

        assertTrue(finished.await(15, TimeUnit.SECONDS), "JavaFX test action timed out.");
        if (failure.get() != null) {
            throw new AssertionError("JavaFX UI test failed.", failure.get());
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
