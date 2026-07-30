package org.pindb.ui;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.pindb.service.BugReportService;
import org.pindb.service.ExternalLinkService;
import org.pindb.service.GitHubAppConfig;
import org.pindb.service.GitHubAuthService;
import org.pindb.service.SettingsService;

import java.net.URI;

public final class BugReportDialog extends Dialog<Void> {
    private final SettingsService settings;
    private final TextField title = new TextField();
    private final TextArea description = area("Describe the problem.");
    private final TextArea steps = area("List the steps that cause the problem.");
    private final TextArea expected = area("What did you expect PinDB to do?");
    private final TextArea actual = area("What did PinDB do instead?");
    private final TextArea additional = area("Optional additional details.");
    private final CheckBox diagnostics = new CheckBox("Include PinDB version and system diagnostics");
    private final Label status = new Label();

    public BugReportDialog(Window owner, SettingsService settings) {
        this.settings = settings;
        initOwner(owner);
        setTitle("Report a PinDB Bug");
        setHeaderText("Create a bug report in the PinDB GitHub repository");
        setResizable(true);
        ButtonType submitType = new ButtonType("Submit Bug Report", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(submitType, ButtonType.CANCEL);

        diagnostics.setSelected(true);
        status.setWrapText(true);
        status.getStyleClass().add("muted-label");
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(8));
        int row = 0;
        addRow(grid, "Title", title, row++);
        addRow(grid, "Description", description, row++);
        addRow(grid, "Steps to reproduce", steps, row++);
        addRow(grid, "Expected behavior", expected, row++);
        addRow(grid, "Actual behavior", actual, row++);
        addRow(grid, "Additional information", additional, row++);
        grid.add(diagnostics, 1, row++);
        grid.add(status, 0, row, 2, 1);
        getDialogPane().setContent(grid);
        getDialogPane().setPrefSize(760, 720);

        Button submit = (Button) getDialogPane().lookupButton(submitType);
        submit.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            event.consume();
            if (title.getText().isBlank() || description.getText().isBlank()) {
                status.setText("Enter a title and description before submitting.");
                return;
            }
            submitReport(submit);
        });
        getDialogPane().sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene != null) {
                UiUtil.applyStyles(newScene, settings);
            }
        });
    }

    private void submitReport(Button submitButton) {
        if (!GitHubAppConfig.configured()) {
            UiUtil.warning(getOwner(), "Bug Reporter Not Configured",
                    "The PinDB GitHub App client ID has not been configured yet. "
                            + "The application owner must finish the GitHub App setup before reports can be submitted.");
            return;
        }
        submitButton.setDisable(true);
        status.setText("Connecting to GitHub…");
        BugReportService.BugReport report = new BugReportService.BugReport(
                title.getText().trim(), description.getText(), steps.getText(), expected.getText(),
                actual.getText(), additional.getText(), diagnostics.isSelected());
        Task<BugReportService.SubmittedIssue> task = new Task<>() {
            @Override
            protected BugReportService.SubmittedIssue call() throws Exception {
                GitHubAuthService auth = new GitHubAuthService();
                String token = auth.accessToken(authorization -> Platform.runLater(() ->
                        showAuthorization(authorization)));
                updateMessage("Submitting report to GitHub…");
                return new BugReportService().submit(token, report);
            }
        };
        status.textProperty().bind(task.messageProperty());
        task.setOnSucceeded(event -> {
            status.textProperty().unbind();
            BugReportService.SubmittedIssue issue = task.getValue();
            close();
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.initOwner(getOwner());
            alert.setTitle("Bug Report Submitted");
            alert.setHeaderText("GitHub issue #" + issue.number() + " was created");
            alert.setContentText("Your bug report was submitted successfully.");
            ButtonType open = new ButtonType("Open Issue", ButtonBar.ButtonData.OTHER);
            alert.getButtonTypes().setAll(open, ButtonType.CLOSE);
            if (alert.showAndWait().orElse(ButtonType.CLOSE) == open) {
                openExternalLink(issue.url(), "Opening the GitHub issue…");
            }
        });
        task.setOnFailed(event -> {
            status.textProperty().unbind();
            submitButton.setDisable(false);
            status.setText("The report could not be submitted.");
            UiUtil.error(getOwner(), "Bug Report Failed",
                    "PinDB could not submit the bug report to GitHub.", task.getException());
        });
        Thread thread = new Thread(task, "pindb-bug-report");
        thread.setDaemon(true);
        thread.start();
    }

    private void showAuthorization(GitHubAuthService.DeviceAuthorization authorization) {
        Label instructions = new Label("Open the GitHub authorization page, enter the code, and approve PinDB. "
                + "This is required only when connecting or reconnecting your GitHub account.");
        instructions.setWrapText(true);
        TextField code = new TextField(authorization.userCode());
        code.setEditable(false);
        TextField address = new TextField(authorization.verificationUri().toString());
        address.setEditable(false);
        VBox content = new VBox(10, instructions, new Label("Device code:"), code,
                new Label("Authorization page:"), address);

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(getOwner());
        alert.setTitle("Authorize PinDB on GitHub");
        alert.setHeaderText("Authorize PinDB using this GitHub device code");
        alert.getDialogPane().setContent(content);
        ButtonType open = new ButtonType("Open GitHub", ButtonBar.ButtonData.OK_DONE);
        ButtonType copy = new ButtonType("Copy Code", ButtonBar.ButtonData.OTHER);
        alert.getButtonTypes().setAll(open, copy, ButtonType.CLOSE);

        ButtonType selected = alert.showAndWait().orElse(ButtonType.CLOSE);
        if (selected == copy) {
            ClipboardContent clipboard = new ClipboardContent();
            clipboard.putString(authorization.userCode());
            Clipboard.getSystemClipboard().setContent(clipboard);
            status.setText("GitHub device code copied. Complete authorization in your browser.");
        } else if (selected == open) {
            openExternalLink(authorization.verificationUri(),
                    "Opening GitHub… Complete authorization with code " + authorization.userCode() + ".");
        }
    }

    private void openExternalLink(URI uri, String progressMessage) {
        status.setText(progressMessage);
        ExternalLinkService.openAsync(uri).thenAccept(opened -> Platform.runLater(() -> {
            if (opened) {
                status.setText("Waiting for GitHub authorization…");
            } else {
                status.setText("Could not open the browser automatically. Use this address manually: " + uri);
            }
        }));
    }

    private static TextArea area(String prompt) {
        TextArea area = new TextArea();
        area.setPromptText(prompt);
        area.setPrefRowCount(3);
        area.setWrapText(true);
        return area;
    }

    private static void addRow(GridPane grid, String text, javafx.scene.Node node, int row) {
        Label label = new Label(text + ":");
        label.setWrapText(true);
        grid.add(label, 0, row);
        grid.add(node, 1, row);
        GridPane.setHgrow(node, Priority.ALWAYS);
    }
}
