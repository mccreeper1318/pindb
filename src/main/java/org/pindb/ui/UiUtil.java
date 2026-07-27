package org.pindb.ui;

import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Region;
import javafx.stage.Window;
import org.pindb.model.FieldDefinition;
import org.pindb.model.FieldType;
import org.pindb.service.SettingsService;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Objects;

public final class UiUtil {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, uuuu");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("MMM d, uuuu h:mm a");

    private UiUtil() {
    }

    public static void applyStyles(Scene scene, SettingsService settings) {
        scene.getStylesheets().clear();
        String base = Objects.requireNonNull(UiUtil.class.getResource("/org/pindb/app/app.css")).toExternalForm();
        scene.getStylesheets().add(base);
        if (settings.theme() == SettingsService.Theme.DARK) {
            scene.getStylesheets().add(Objects.requireNonNull(
                    UiUtil.class.getResource("/org/pindb/app/dark.css")).toExternalForm());
        }
    }

    public static Button primaryButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("primary");
        return button;
    }

    public static Label title(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("title-label");
        return label;
    }

    public static void information(Window owner, String title, String message) {
        Alert alert = alert(owner, Alert.AlertType.INFORMATION, title, message);
        alert.showAndWait();
    }

    public static void warning(Window owner, String title, String message) {
        Alert alert = alert(owner, Alert.AlertType.WARNING, title, message);
        alert.showAndWait();
    }

    public static void error(Window owner, String title, String message, Throwable exception) {
        Alert alert = alert(owner, Alert.AlertType.ERROR, title, message);
        if (exception != null) {
            TextArea details = new TextArea(stackTrace(exception));
            details.setEditable(false);
            details.setWrapText(false);
            details.setMaxWidth(Double.MAX_VALUE);
            details.setMaxHeight(Double.MAX_VALUE);
            alert.getDialogPane().setExpandableContent(details);
        }
        alert.showAndWait();
    }

    public static boolean confirm(Window owner, String title, String message) {
        Alert alert = alert(owner, Alert.AlertType.CONFIRMATION, title, message);
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private static Alert alert(Window owner, Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        if (owner != null) {
            alert.initOwner(owner);
        }
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        return alert;
    }

    public static String formatValue(FieldDefinition field, String value) {
        String safe = Objects.requireNonNullElse(value, "");
        if (safe.isBlank()) {
            return "";
        }
        try {
            return switch (field.type()) {
                case CURRENCY -> NumberFormat.getCurrencyInstance(Locale.getDefault()).format(new BigDecimal(safe));
                case NUMBER -> new BigDecimal(safe).stripTrailingZeros().toPlainString();
                case DATE -> DATE_FORMAT.format(LocalDate.parse(safe));
                case DATE_TIME -> DATE_TIME_FORMAT.format(LocalDateTime.parse(safe));
                case BOOLEAN -> Boolean.parseBoolean(safe) ? "Yes" : "No";
                default -> safe;
            };
        } catch (RuntimeException exception) {
            return safe;
        }
    }

    public static String resolvedDefault(FieldDefinition field) {
        String value = field.defaultValue();
        if ("${TODAY}".equals(value)) {
            return LocalDate.now().toString();
        }
        if ("${NOW}".equals(value)) {
            return LocalDateTime.now().withSecond(0).withNano(0).toString();
        }
        if (value.isBlank() && field.type() == FieldType.DATE) {
            return LocalDate.now().toString();
        }
        if (value.isBlank() && field.type() == FieldType.DATE_TIME) {
            return LocalDateTime.now().withSecond(0).withNano(0).toString();
        }
        return value;
    }

    public static LocalDate parseDate(String value) {
        try {
            return value == null || value.isBlank() ? LocalDate.now() : LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            return LocalDate.now();
        }
    }

    private static String stackTrace(Throwable throwable) {
        java.io.StringWriter writer = new java.io.StringWriter();
        throwable.printStackTrace(new java.io.PrintWriter(writer));
        return writer.toString();
    }
}
