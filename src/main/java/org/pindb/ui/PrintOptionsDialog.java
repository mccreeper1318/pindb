package org.pindb.ui;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.pindb.db.DatabaseService;
import org.pindb.model.FieldDefinition;
import org.pindb.model.PrintArrangement;
import org.pindb.model.PrintOptions;
import org.pindb.service.SettingsService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PrintOptionsDialog extends Dialog<PrintOptions> {
    public PrintOptionsDialog(Window owner, SettingsService settings, DatabaseService database,
                              List<FieldDefinition> fields) {
        initOwner(owner);
        setTitle("Print Database");
        setHeaderText("Choose how the database should be formatted");
        ButtonType print = new ButtonType("Continue to Printer", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(print, ButtonType.CANCEL);

        ComboBox<PrintArrangement> arrangement = new ComboBox<>(
                FXCollections.observableArrayList(PrintArrangement.values()));
        arrangement.setValue(parseArrangement(database.getMeta("print_layout", "COLUMNS")));
        arrangement.setMaxWidth(Double.MAX_VALUE);

        ComboBox<String> orientation = new ComboBox<>(
                FXCollections.observableArrayList("Portrait", "Landscape"));
        orientation.setValue("LANDSCAPE".equalsIgnoreCase(database.getMeta("print_orientation", "LANDSCAPE"))
                ? "Landscape" : "Portrait");
        orientation.setMaxWidth(Double.MAX_VALUE);

        CheckBox databaseName = new CheckBox("Database name in header");
        databaseName.setSelected(true);
        CheckBox printDate = new CheckBox("Print date in header");
        printDate.setSelected(true);
        CheckBox pageNumbers = new CheckBox("Page numbers");
        pageNumbers.setSelected(true);
        CheckBox repeatHeadings = new CheckBox("Repeat field headings on every page");
        repeatHeadings.setSelected(true);

        Map<Long, CheckBox> fieldChecks = new LinkedHashMap<>();
        VBox fieldBox = new VBox(7);
        for (FieldDefinition field : fields) {
            CheckBox check = new CheckBox(field.name());
            check.setSelected(true);
            fieldChecks.put(field.id(), check);
            fieldBox.getChildren().add(check);
        }
        ScrollPane fieldScroll = new ScrollPane(fieldBox);
        fieldScroll.setFitToWidth(true);
        fieldScroll.setPrefViewportHeight(180);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(8));
        grid.add(new Label("Arrangement:"), 0, 0);
        grid.add(arrangement, 1, 0);
        grid.add(new Label("Orientation:"), 0, 1);
        grid.add(orientation, 1, 1);
        grid.add(databaseName, 0, 2, 2, 1);
        grid.add(printDate, 0, 3, 2, 1);
        grid.add(pageNumbers, 0, 4, 2, 1);
        grid.add(repeatHeadings, 0, 5, 2, 1);
        grid.add(new Label("Fields to print:"), 0, 6, 2, 1);
        grid.add(fieldScroll, 0, 7, 2, 1);
        getDialogPane().setContent(grid);
        getDialogPane().setPrefWidth(560);

        setResultConverter(button -> {
            if (button != print) {
                return null;
            }
            List<Long> chosen = fieldChecks.entrySet().stream()
                    .filter(entry -> entry.getValue().isSelected())
                    .map(Map.Entry::getKey)
                    .toList();
            if (chosen.isEmpty()) {
                UiUtil.warning(owner, "No Fields Selected", "Select at least one field to print.");
                return null;
            }
            PrintArrangement selected = arrangement.getValue() == null
                    ? PrintArrangement.COLUMNS : arrangement.getValue();
            boolean landscape = "Landscape".equals(orientation.getValue());
            database.setMeta("print_layout", selected.name());
            database.setMeta("print_orientation", landscape ? "LANDSCAPE" : "PORTRAIT");
            return new PrintOptions(selected, landscape, databaseName.isSelected(), printDate.isSelected(),
                    pageNumbers.isSelected(), repeatHeadings.isSelected(), chosen);
        });
        getDialogPane().sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene != null) {
                UiUtil.applyStyles(newScene, settings);
            }
        });
    }

    private static PrintArrangement parseArrangement(String value) {
        try {
            return PrintArrangement.valueOf(value);
        } catch (RuntimeException exception) {
            return PrintArrangement.COLUMNS;
        }
    }
}
