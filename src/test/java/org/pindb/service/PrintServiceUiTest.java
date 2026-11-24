package org.pindb.service;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.pindb.model.DatabaseInfo;
import org.pindb.model.DatabaseView;
import org.pindb.model.FieldDefinition;
import org.pindb.model.FieldType;
import org.pindb.model.PrintArrangement;
import org.pindb.model.PrintOptions;
import org.pindb.model.RecordData;
import org.pindb.model.SummaryType;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "PINDB_RUN_JAVAFX_TESTS", matches = "true")
class PrintServiceUiTest {

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
    void wrappedTableCellsShareTallestRowHeightAndExplicitBorders() throws Exception {
        runOnFxThread(() -> {
            FieldDefinition notes = field(1, "Notes", FieldType.MULTILINE_TEXT, 0);
            FieldDefinition email = field(2, "Email", FieldType.TEXT, 1);
            GridPane grid = PrintService.tableGrid(List.of(notes, email), 150);

            Label wrapped = PrintService.tableCell(
                    "This is a deliberately long notes value that wraps across several lines "
                            + "so the printable table row must become much taller than a short neighboring value.",
                    false);
            Label shortValue = PrintService.tableCell("user@example.com", false);
            grid.add(wrapped, 0, 0);
            grid.add(shortValue, 1, 0);

            StackPane root = new StackPane(grid);
            Scene scene = new Scene(root, 320, 260);
            Stage stage = new Stage();
            try {
                stage.setScene(scene);
                stage.show();
                root.applyCss();
                root.layout();

                assertFalse(grid.isGridLinesVisible(),
                        "Printable tables should not depend on GridPane debug grid lines.");
                assertTrue(wrapped.getHeight() > 40,
                        "The long value should produce a wrapped, expanded row.");
                assertEquals(wrapped.getHeight(), shortValue.getHeight(), 0.5,
                        "Every cell must fill the tallest wrapped cell's resolved row height.");
                assertTrue(wrapped.getStyle().contains("-fx-border-width: 0 1 1 0;"));
                assertTrue(shortValue.getStyle().contains("-fx-border-width: 0 1 1 0;"));
            } finally {
                stage.close();
            }
        });
    }

    @Test
    void generatedPreviewPagesUseTheSamePrintConfigurationAsFinalOutput() throws Exception {
        runOnFxThread(() -> {
            FieldDefinition name = field(1, "Name", FieldType.TEXT, 0);
            FieldDefinition hidden = field(2, "Internal Notes", FieldType.TEXT, 1);
            LocalDateTime now = LocalDateTime.of(2026, 11, 24, 8, 0);
            RecordData record = new RecordData(1, now, now, null,
                    new LinkedHashMap<>(Map.of(1L, "Example Entry", 2L, "Do not print")));

            PrintOptions options = new PrintOptions(
                    PrintArrangement.COLUMNS,
                    false,
                    true,
                    false,
                    true,
                    true,
                    false,
                    List.of(name.id()));
            DatabaseInfo info = new DatabaseInfo(
                    Path.of("preview.pindb"), "Preview Database", "", 1,
                    DatabaseView.TABLE, false, 10);

            List<Node> pages = PrintService.printablePages(
                    info, List.of(name, hidden), List.of(record), options,
                    new PrintService.PrintArea(520, 700));

            assertEquals(1, pages.size());
            BorderPane page = (BorderPane) pages.getFirst();
            HBox header = (HBox) page.getTop();
            GridPane body = (GridPane) page.getCenter();
            Label footer = (Label) page.getBottom();

            List<String> headerText = header.getChildren().stream()
                    .filter(Label.class::isInstance)
                    .map(Label.class::cast)
                    .map(Label::getText)
                    .toList();
            List<String> bodyText = body.getChildren().stream()
                    .filter(Label.class::isInstance)
                    .map(Label.class::cast)
                    .map(Label::getText)
                    .toList();

            assertTrue(headerText.contains("Preview Database"));
            assertFalse(headerText.stream().anyMatch(text -> text.startsWith("Printed ")));
            assertEquals("Page 1 of 1", footer.getText());
            assertTrue(bodyText.contains("Name"));
            assertTrue(bodyText.contains("Example Entry"));
            assertFalse(bodyText.contains("Internal Notes"));
            assertFalse(bodyText.contains("Do not print"));
        });
    }

    private static FieldDefinition field(long id, String name, FieldType type, int position) {
        return new FieldDefinition(id, name, type, position,
                false, "", "", "", false, null, List.of(), SummaryType.NONE);
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
            throw new AssertionError("JavaFX print layout test failed.", failure.get());
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
