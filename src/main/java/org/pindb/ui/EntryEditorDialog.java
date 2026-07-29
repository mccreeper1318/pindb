package org.pindb.ui;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.pindb.model.DocumentData;
import org.pindb.model.FieldDefinition;
import org.pindb.model.RecordData;
import org.pindb.service.SettingsService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class EntryEditorDialog extends Dialog<EntryEditorDialog.Result> {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("H:mm");
    private final List<FieldDefinition> fields;
    private final Map<Long, ValueEditor> editors = new LinkedHashMap<>();
    private final Map<Long, DocumentData> documents = new LinkedHashMap<>();
    private final Label error = new Label();

    public EntryEditorDialog(Window owner, SettingsService settings, List<FieldDefinition> fields, RecordData existing) {
        this(owner, settings, fields, existing, Map.of());
    }

    public EntryEditorDialog(Window owner, SettingsService settings, List<FieldDefinition> fields,
                             RecordData existing, Map<Long, DocumentData> existingDocuments) {
        this.fields = fields;
        documents.putAll(existingDocuments == null ? Map.of() : existingDocuments);
        initOwner(owner);
        setTitle(existing == null ? "New Entry" : "Edit Entry");
        setHeaderText(existing == null ? "Add an entry to the database" : "Edit entry " + existing.id());

        ButtonType hiddenCancelType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().add(hiddenCancelType);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(8));
        int row = 0;
        for (FieldDefinition field : fields) {
            String labelText = field.name() + (field.required() ? " *" : "");
            Label label = new Label(labelText + ":");
            label.setWrapText(true);
            String value = existing == null ? UiUtil.resolvedDefault(field) : existing.value(field.id());
            ValueEditor editor = createEditor(field, value);
            editors.put(field.id(), editor);
            grid.add(label, 0, row);
            grid.add(editor.node(), 1, row);
            GridPane.setHgrow(editor.node(), Priority.ALWAYS);
            row++;
        }
        error.getStyleClass().add("error-label");
        error.setWrapText(true);
        grid.add(error, 0, row, 2, 1);

        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(Math.min(650, 90 + fields.size() * 58));

        Button cancelButton = new Button("Cancel");
        cancelButton.setCancelButton(true);
        cancelButton.setOnAction(event -> {
            setResult(null);
            close();
        });

        Button saveButton = new Button(existing == null ? "Add Entry" : "Save Changes");
        saveButton.setDefaultButton(true);
        saveButton.getStyleClass().add("primary");
        saveButton.setOnAction(event -> saveAndClose(false));

        HBox actions;
        if (existing == null) {
            Button addMoreButton = new Button("Add & Add Another");
            addMoreButton.setOnAction(event -> saveAndClose(true));
            actions = new HBox(10, cancelButton, addMoreButton, saveButton);
        } else {
            actions = new HBox(10, cancelButton, saveButton);
        }
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.setPadding(new Insets(0, 8, 8, 8));

        VBox content = new VBox(12, scroll, actions);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        getDialogPane().setContent(content);
        getDialogPane().setPrefWidth(700);

        getDialogPane().sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene != null) {
                UiUtil.applyStyles(newScene, settings);
            }
        });
        setOnShown(event -> hidePlatformButtonBar(hiddenCancelType));
    }

    private void hidePlatformButtonBar(ButtonType hiddenCancelType) {
        getDialogPane().applyCss();
        Node hiddenCancelButton = getDialogPane().lookupButton(hiddenCancelType);
        if (hiddenCancelButton != null) {
            hiddenCancelButton.setVisible(false);
            hiddenCancelButton.setManaged(false);
        }
        Node buttonBar = getDialogPane().lookup(".button-bar");
        if (buttonBar != null) {
            buttonBar.setVisible(false);
            buttonBar.setManaged(false);
        }
    }

    private void saveAndClose(boolean addAnother) {
        String validation = validateEditors();
        if (!validation.isBlank()) {
            error.setText(validation);
            return;
        }
        error.setText("");
        setResult(new Result(values(), Map.copyOf(documents), addAnother));
        close();
    }

    private ValueEditor createEditor(FieldDefinition field, String value) {
        return switch (field.type()) {
            case TEXT, NUMBER, CURRENCY -> textField(value);
            case MULTILINE_TEXT -> textArea(value);
            case DATE -> dateEditor(value);
            case DATE_TIME -> dateTimeEditor(value);
            case BOOLEAN -> booleanEditor(value);
            case DROPDOWN -> dropdownEditor(field, value);
            case DOCUMENT -> documentEditor(field, documents.get(field.id()));
        };
    }

    private ValueEditor textField(String value) {
        TextField field = new TextField(value);
        field.setMaxWidth(Double.MAX_VALUE);
        return new ValueEditor(field, () -> field.getText().trim(), () -> "");
    }

    private ValueEditor textArea(String value) {
        TextArea area = new TextArea(value);
        area.setPrefRowCount(3);
        area.setWrapText(true);
        return new ValueEditor(area, () -> area.getText().trim(), () -> "");
    }

    private ValueEditor dateEditor(String value) {
        DatePicker picker = new DatePicker(UiUtil.parseDate(value));
        picker.setMaxWidth(Double.MAX_VALUE);
        return new ValueEditor(picker,
                () -> {
                    LocalDate committed = committedDate(picker);
                    return committed == null ? "" : committed.toString();
                },
                () -> {
                    try {
                        committedDate(picker);
                        return "";
                    } catch (RuntimeException exception) {
                        return "Enter a valid date.";
                    }
                });
    }

    private LocalDate committedDate(DatePicker picker) {
        String typed = picker.getEditor().getText() == null ? "" : picker.getEditor().getText().trim();
        if (typed.isBlank()) {
            picker.setValue(null);
            return null;
        }
        LocalDate parsed = picker.getConverter().fromString(typed);
        picker.setValue(parsed);
        return parsed;
    }

    private ValueEditor dateTimeEditor(String value) {
        LocalDateTime parsed;
        try {
            parsed = value == null || value.isBlank()
                    ? LocalDateTime.now().withSecond(0).withNano(0)
                    : LocalDateTime.parse(value);
        } catch (DateTimeParseException exception) {
            parsed = LocalDateTime.now().withSecond(0).withNano(0);
        }
        DatePicker date = new DatePicker(parsed.toLocalDate());
        TextField time = new TextField(TIME_FORMAT.format(parsed.toLocalTime()));
        time.setPromptText("14:30");
        time.setPrefColumnCount(7);
        HBox box = new HBox(8, date, time);
        HBox.setHgrow(date, Priority.ALWAYS);
        return new ValueEditor(box, () -> {
            LocalDate committed = committedDate(date);
            if (committed == null || time.getText().isBlank()) {
                return "";
            }
            return LocalDateTime.of(committed,
                    LocalTime.parse(time.getText().trim(), TIME_FORMAT)).toString();
        }, () -> {
            if (date.getEditor().getText().isBlank() && time.getText().isBlank()) {
                return "";
            }
            try {
                LocalDate committed = committedDate(date);
                if (committed == null || time.getText().isBlank()) {
                    return "Enter both a date and a time.";
                }
                LocalTime.parse(time.getText().trim(), TIME_FORMAT);
                return "";
            } catch (DateTimeParseException exception) {
                return "Enter a valid date and time, such as 14:30.";
            } catch (RuntimeException exception) {
                return "Enter a valid date and time.";
            }
        });
    }

    private ValueEditor booleanEditor(String value) {
        CheckBox check = new CheckBox("Yes");
        check.setSelected(Boolean.parseBoolean(value));
        return new ValueEditor(check, () -> Boolean.toString(check.isSelected()), () -> "");
    }

    private ValueEditor dropdownEditor(FieldDefinition field, String value) {
        ComboBox<String> combo = new ComboBox<>(FXCollections.observableArrayList(field.dropdownOptions()));
        combo.setEditable(false);
        combo.setMaxWidth(Double.MAX_VALUE);
        if (field.dropdownOptions().contains(value)) {
            combo.setValue(value);
        } else if (!value.isBlank()) {
            combo.getItems().add(value);
            combo.setValue(value);
        } else if (!field.defaultValue().isBlank() && field.dropdownOptions().contains(field.defaultValue())) {
            combo.setValue(field.defaultValue());
        }
        return new ValueEditor(combo, () -> combo.getValue() == null ? "" : combo.getValue(), () -> "");
    }

    private ValueEditor documentEditor(FieldDefinition field, DocumentData existing) {
        Label fileName = new Label(existing == null ? "No document selected" : existing.fileName());
        fileName.setWrapText(true);
        fileName.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(fileName, Priority.ALWAYS);

        Button choose = new Button(existing == null ? "Choose…" : "Replace…");
        Button remove = new Button("Remove");
        remove.setDisable(existing == null);
        choose.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Choose a Document for " + field.name());
            File selected = chooser.showOpenDialog(getOwner());
            if (selected == null) {
                return;
            }
            try {
                byte[] data = Files.readAllBytes(selected.toPath());
                String mimeType = Files.probeContentType(selected.toPath());
                DocumentData document = new DocumentData(selected.getName(), mimeType, data);
                documents.put(field.id(), document);
                fileName.setText(document.fileName());
                choose.setText("Replace…");
                remove.setDisable(false);
                error.setText("");
            } catch (IOException exception) {
                error.setText("Could not read " + selected.getName() + ": " + exception.getMessage());
            }
        });
        remove.setOnAction(event -> {
            documents.remove(field.id());
            fileName.setText("No document selected");
            choose.setText("Choose…");
            remove.setDisable(true);
        });

        HBox box = new HBox(10, fileName, choose, remove);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setMaxWidth(Double.MAX_VALUE);
        return new ValueEditor(box,
                () -> {
                    DocumentData document = documents.get(field.id());
                    return document == null ? "" : document.fileName();
                }, () -> "");
    }

    private String validateEditors() {
        for (FieldDefinition field : fields) {
            ValueEditor editor = editors.get(field.id());
            String editorError = editor.validation().get();
            if (!editorError.isBlank()) {
                return field.name() + ": " + editorError;
            }
            String value;
            try {
                value = editor.value().get();
            } catch (RuntimeException exception) {
                return field.name() + " contains an invalid value.";
            }
            if (field.required() && (value == null || value.isBlank())) {
                return field.name() + " is required.";
            }
        }
        return "";
    }

    private Map<Long, String> values() {
        LinkedHashMap<Long, String> values = new LinkedHashMap<>();
        for (FieldDefinition field : fields) {
            String value = editors.get(field.id()).value().get();
            values.put(field.id(), value == null ? "" : value);
        }
        return values;
    }

    public record Result(Map<Long, String> values, Map<Long, DocumentData> documents, boolean addAnother) {
        public Result {
            values = Map.copyOf(values);
            documents = Map.copyOf(documents);
        }
    }

    private record ValueEditor(Node node, java.util.function.Supplier<String> value,
                               java.util.function.Supplier<String> validation) {
    }
}
