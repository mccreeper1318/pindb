package org.pindb.ui;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;
import org.pindb.model.FieldDefinition;
import org.pindb.model.FieldType;
import org.pindb.model.SummaryType;
import org.pindb.service.SettingsService;

import java.util.Arrays;
import java.util.List;

public final class FieldEditorDialog extends Dialog<FieldDefinition> {
    private final TextField name = new TextField();
    private final ComboBox<FieldType> type = new ComboBox<>();
    private final CheckBox required = new CheckBox("Required field");
    private final TextField defaultValue = new TextField();
    private final CheckBox useCurrentDate = new CheckBox("Use the current date/time by default");
    private final TextField minimum = new TextField();
    private final TextField maximum = new TextField();
    private final CheckBox unique = new CheckBox("Values must be unique");
    private final Spinner<Integer> characterLimit = new Spinner<>(0, 1_000_000, 0, 10);
    private final TextArea dropdownOptions = new TextArea();
    private final ComboBox<SummaryType> summary = new ComboBox<>();
    private final Label error = new Label();
    private final GridPane grid = new GridPane();
    private final FieldDefinition original;

    public FieldEditorDialog(Window owner, SettingsService settings, FieldDefinition existing) {
        original = existing == null ? new FieldDefinition() : existing.copy();
        initOwner(owner);
        setTitle(existing == null ? "Add Field" : "Edit Field");
        setHeaderText(existing == null ? "Create a database field" : "Edit " + existing.name());

        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        type.setItems(FXCollections.observableArrayList(FieldType.values()));
        type.setMaxWidth(Double.MAX_VALUE);
        summary.setMaxWidth(Double.MAX_VALUE);
        dropdownOptions.setPrefRowCount(4);
        dropdownOptions.setPromptText("One option per line");
        characterLimit.setEditable(true);
        error.getStyleClass().add("error-label");
        error.setWrapText(true);

        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));
        rebuildGrid();
        getDialogPane().setContent(grid);
        getDialogPane().setPrefWidth(560);

        loadOriginal();
        useCurrentDate.selectedProperty().addListener((observable, oldValue, newValue) -> updateForType());
        updateForType();
        type.valueProperty().addListener((observable, oldValue, newValue) -> updateForType());

        Button save = (Button) getDialogPane().lookupButton(saveType);
        save.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            String validation = validateInput();
            if (!validation.isBlank()) {
                error.setText(validation);
                event.consume();
            }
        });

        setResultConverter(button -> button == saveType ? buildResult() : null);
        getDialogPane().sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene != null) {
                UiUtil.applyStyles(newScene, settings);
            }
        });
    }

    private void rebuildGrid() {
        grid.getChildren().clear();
        int row = 0;
        addRow("Field name", name, row++);
        addRow("Field type", type, row++);
        grid.add(required, 1, row++);
        addRow("Default value", defaultValue, row++);
        grid.add(useCurrentDate, 1, row++);
        addRow("Minimum", minimum, row++);
        addRow("Maximum", maximum, row++);
        grid.add(unique, 1, row++);
        addRow("Character limit", characterLimit, row++);
        addRow("Dropdown options", dropdownOptions, row++);
        addRow("Summary", summary, row++);
        grid.add(error, 0, row, 2, 1);
        GridPane.setHgrow(name, javafx.scene.layout.Priority.ALWAYS);
        GridPane.setHgrow(defaultValue, javafx.scene.layout.Priority.ALWAYS);
        GridPane.setHgrow(dropdownOptions, javafx.scene.layout.Priority.ALWAYS);
    }

    private void addRow(String label, javafx.scene.Node node, int row) {
        Label item = new Label(label + ":");
        grid.add(item, 0, row);
        grid.add(node, 1, row);
    }

    private void loadOriginal() {
        name.setText(original.name());
        type.setValue(original.type());
        required.setSelected(original.required());
        boolean specialDate = "${TODAY}".equals(original.defaultValue()) || "${NOW}".equals(original.defaultValue());
        useCurrentDate.setSelected(specialDate);
        defaultValue.setText(specialDate ? "" : original.defaultValue());
        minimum.setText(original.minValue());
        maximum.setText(original.maxValue());
        unique.setSelected(original.uniqueValue());
        characterLimit.getValueFactory().setValue(original.characterLimit() == null ? 0 : original.characterLimit());
        dropdownOptions.setText(String.join("\n", original.dropdownOptions()));
        summary.setValue(original.summaryType());
    }

    private void updateForType() {
        FieldType selected = type.getValue() == null ? FieldType.TEXT : type.getValue();
        boolean numeric = selected.isNumeric();
        boolean date = selected == FieldType.DATE || selected == FieldType.DATE_TIME;
        boolean text = selected == FieldType.TEXT || selected == FieldType.MULTILINE_TEXT;
        boolean dropdown = selected == FieldType.DROPDOWN;

        minimum.setDisable(!numeric);
        maximum.setDisable(!numeric);
        useCurrentDate.setDisable(!date);
        defaultValue.setDisable(date && useCurrentDate.isSelected());
        characterLimit.setDisable(!text);
        dropdownOptions.setDisable(!dropdown);

        List<SummaryType> choices = numeric
                ? Arrays.asList(SummaryType.values())
                : selected.supportsSummary()
                ? List.of(SummaryType.NONE, SummaryType.COUNT)
                : List.of(SummaryType.NONE);
        SummaryType previous = summary.getValue();
        summary.setItems(FXCollections.observableArrayList(choices));
        summary.setValue(choices.contains(previous) ? previous : SummaryType.NONE);

    }

    private String validateInput() {
        if (name.getText().isBlank()) {
            return "Enter a name for this field.";
        }
        if (type.getValue() == null) {
            return "Choose a field type.";
        }
        if (type.getValue().isNumeric()) {
            try {
                if (!minimum.getText().isBlank()) {
                    new java.math.BigDecimal(minimum.getText().trim());
                }
                if (!maximum.getText().isBlank()) {
                    new java.math.BigDecimal(maximum.getText().trim());
                }
            } catch (NumberFormatException exception) {
                return "Minimum and maximum values must be valid numbers.";
            }
        }
        if (type.getValue() == FieldType.DROPDOWN && dropdownOptions.getText().lines()
                .map(String::trim).filter(value -> !value.isBlank()).findAny().isEmpty()) {
            return "Add at least one dropdown option.";
        }
        return "";
    }

    private FieldDefinition buildResult() {
        FieldDefinition result = original.copy();
        result.setName(name.getText());
        result.setType(type.getValue());
        result.setRequired(required.isSelected());
        result.setDefaultValue(useCurrentDate.isSelected()
                ? (type.getValue() == FieldType.DATE ? "${TODAY}" : "${NOW}")
                : defaultValue.getText().trim());
        result.setMinValue(minimum.getText().trim());
        result.setMaxValue(maximum.getText().trim());
        result.setUniqueValue(unique.isSelected());
        result.setCharacterLimit(characterLimit.getValue() == 0 ? null : characterLimit.getValue());
        result.setDropdownOptions(dropdownOptions.getText().lines().map(String::trim).filter(value -> !value.isBlank()).toList());
        result.setSummaryType(summary.getValue() == null ? SummaryType.NONE : summary.getValue());
        return result;
    }
}
