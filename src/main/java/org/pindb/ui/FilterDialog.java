package org.pindb.ui;

import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;
import org.pindb.model.FieldDefinition;
import org.pindb.model.FieldType;
import org.pindb.model.FilterSpec;
import org.pindb.service.SettingsService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class FilterDialog extends Dialog<FilterSpec> {
    private final ComboBox<FieldDefinition> dateField = new ComboBox<>();
    private final DatePicker dateFrom = new DatePicker();
    private final DatePicker dateTo = new DatePicker();
    private final ComboBox<FieldDefinition> numericField = new ComboBox<>();
    private final TextField numericMinimum = new TextField();
    private final TextField numericMaximum = new TextField();
    private final ComboBox<FieldDefinition> choiceField = new ComboBox<>();
    private final ComboBox<String> choiceValue = new ComboBox<>();
    private final Label error = new Label();

    public FilterDialog(Window owner, SettingsService settings, List<FieldDefinition> fields, FilterSpec current) {
        initOwner(owner);
        setTitle("Filter Entries");
        setHeaderText("Filter by date, number, or a selected value");
        ButtonType applyType = new ButtonType("Apply Filter", ButtonBar.ButtonData.OK_DONE);
        ButtonType clearType = new ButtonType("Clear Filter", ButtonBar.ButtonData.LEFT);
        getDialogPane().getButtonTypes().addAll(applyType, clearType, ButtonType.CANCEL);

        dateField.setItems(FXCollections.observableArrayList(fields.stream()
                .filter(field -> field.type() == FieldType.DATE || field.type() == FieldType.DATE_TIME).toList()));
        numericField.setItems(FXCollections.observableArrayList(fields.stream().filter(field -> field.type().isNumeric()).toList()));
        choiceField.setItems(FXCollections.observableArrayList(fields.stream()
                .filter(field -> field.type() == FieldType.DROPDOWN || field.type() == FieldType.BOOLEAN).toList()));
        choiceField.valueProperty().addListener((observable, oldValue, newValue) -> updateChoices(newValue));
        for (ComboBox<?> combo : List.of(dateField, numericField, choiceField, choiceValue)) {
            combo.setMaxWidth(Double.MAX_VALUE);
        }
        numericMinimum.setPromptText("No minimum");
        numericMaximum.setPromptText("No maximum");
        error.getStyleClass().add("error-label");
        error.setWrapText(true);

        loadCurrent(fields, current == null ? FilterSpec.empty() : current);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(new Label("Date field:"), 0, 0);
        grid.add(dateField, 1, 0, 2, 1);
        grid.add(new Label("From:"), 0, 1);
        grid.add(dateFrom, 1, 1);
        grid.add(new Label("To:"), 0, 2);
        grid.add(dateTo, 1, 2);
        grid.add(new javafx.scene.control.Separator(), 0, 3, 3, 1);
        grid.add(new Label("Number field:"), 0, 4);
        grid.add(numericField, 1, 4, 2, 1);
        grid.add(new Label("Minimum:"), 0, 5);
        grid.add(numericMinimum, 1, 5);
        grid.add(new Label("Maximum:"), 0, 6);
        grid.add(numericMaximum, 1, 6);
        grid.add(new javafx.scene.control.Separator(), 0, 7, 3, 1);
        grid.add(new Label("Choice field:"), 0, 8);
        grid.add(choiceField, 1, 8, 2, 1);
        grid.add(new Label("Value:"), 0, 9);
        grid.add(choiceValue, 1, 9, 2, 1);
        grid.add(error, 0, 10, 3, 1);
        getDialogPane().setContent(grid);
        getDialogPane().setPrefWidth(560);

        Button apply = (Button) getDialogPane().lookupButton(applyType);
        apply.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            String validation = validateInput();
            if (!validation.isBlank()) {
                error.setText(validation);
                event.consume();
            }
        });

        setResultConverter(button -> {
            if (button == clearType) {
                return FilterSpec.empty();
            }
            return button == applyType ? buildResult() : null;
        });
        getDialogPane().sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene != null) {
                UiUtil.applyStyles(newScene, settings);
            }
        });
    }

    private void loadCurrent(List<FieldDefinition> fields, FilterSpec current) {
        if (current.dateFieldId() != null) {
            dateField.setValue(find(fields, current.dateFieldId()));
            dateFrom.setValue(current.dateFrom());
            dateTo.setValue(current.dateTo());
        }
        if (current.numericFieldId() != null) {
            numericField.setValue(find(fields, current.numericFieldId()));
            numericMinimum.setText(current.numericMinimum() == null ? "" : current.numericMinimum().toPlainString());
            numericMaximum.setText(current.numericMaximum() == null ? "" : current.numericMaximum().toPlainString());
        }
        if (current.choiceFieldId() != null) {
            FieldDefinition field = find(fields, current.choiceFieldId());
            choiceField.setValue(field);
            updateChoices(field);
            choiceValue.setValue(current.choiceValue());
        }
    }

    private void updateChoices(FieldDefinition field) {
        List<String> options = new ArrayList<>();
        if (field != null) {
            if (field.type() == FieldType.BOOLEAN) {
                options.addAll(List.of("true", "false"));
            } else {
                options.addAll(field.dropdownOptions());
            }
        }
        choiceValue.setItems(FXCollections.observableArrayList(options));
        if (!options.contains(choiceValue.getValue())) {
            choiceValue.setValue(null);
        }
    }

    private String validateInput() {
        try {
            if (!numericMinimum.getText().isBlank()) {
                new BigDecimal(numericMinimum.getText().trim());
            }
            if (!numericMaximum.getText().isBlank()) {
                new BigDecimal(numericMaximum.getText().trim());
            }
        } catch (NumberFormatException exception) {
            return "Minimum and maximum must be valid numbers.";
        }
        if (dateFrom.getValue() != null && dateTo.getValue() != null && dateFrom.getValue().isAfter(dateTo.getValue())) {
            return "The starting date cannot be after the ending date.";
        }
        return "";
    }

    private FilterSpec buildResult() {
        return new FilterSpec(
                dateField.getValue() == null ? null : dateField.getValue().id(),
                dateFrom.getValue(), dateTo.getValue(),
                numericField.getValue() == null ? null : numericField.getValue().id(),
                numericMinimum.getText().isBlank() ? null : new BigDecimal(numericMinimum.getText().trim()),
                numericMaximum.getText().isBlank() ? null : new BigDecimal(numericMaximum.getText().trim()),
                choiceField.getValue() == null ? null : choiceField.getValue().id(),
                choiceValue.getValue() == null ? "" : choiceValue.getValue()
        );
    }

    private static FieldDefinition find(List<FieldDefinition> fields, long id) {
        return fields.stream().filter(field -> field.id() == id).findFirst().orElse(null);
    }
}
