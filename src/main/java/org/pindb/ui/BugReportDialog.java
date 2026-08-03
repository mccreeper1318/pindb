package org.pindb.ui;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
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
    private final Window owner;
    private final SettingsService settings;
    private final TextField title = new TextField();
    private final TextArea description = area("Describe the problem.");
    private final TextArea steps = area("List the steps that cause the problem.");
    private final TextArea expected = area("What did you expect PinDB to do?");
    private final TextArea actual = area("What did PinDB do instead?");
    private final TextArea additional = area("Optional additional details.");
    private final CheckBox diagnostics = new CheckBox("Include PinDB version and system diagnostics");
    private final Label status = new Label();

    private Task<BugReportService.SubmittedIssue> activeSubmission;
    private Dialog<Void> authorizationDialog;
    private boolean closingAuthorizationProgrammatically;

    public BugReportDialog(Window owner, SettingsService settings) {
        this.owner = owner;
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
        submit.addEventFilter(ActionEvent.ACTION, event -> {
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
        setOnHidden(event -> cancelActiveSubmission());
    }

    private void submitReport(Button submitButton) {
        if (!GitHubAppConfig.configured()) {
            UiUtil.warning(owner, "Bug Reporter Not Configured",
                    "The PinDB GitHub App client ID has not been configured yet. "
                            + "The application owner must finish the GitHub App setup before reports can be submitted.");
            return;
        }
        if (activeSubmission != null && activeSubmission.isRunning()) {
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
        activeSubmission = task;
        task.messageProperty().addListener((observable, oldMessage, newMessage) -> {
            if (newMessage != null && !newMessage.isBlank()) {
                status.setText(newMessage);
            }
        });

        task.setOnSucceeded(event -> {
            closeAuthorizationDialog();
            activeSubmission = null;
            submitButton.setDisable(false);
            BugReportService.SubmittedIssue issue = task.getValue();
            close();
            Platform.runLater(() -> showSubmissionSuccess(issue));
        });
        task.setOnFailed(event -> {
            closeAuthorizationDialog();
            activeSubmission = null;
            submitButton.setDisable(false);
            status.setText("The report could not be submitted.");
            UiUtil.error(owner, "Bug Report Failed",
                    "PinDB could not submit the bug report to GitHub.", task.getException());
        });
        task.setOnCancelled(event -> {
            closeAuthorizationDialog();
            activeSubmission = null;
            submitButton.setDisable(false);
            if (isShowing()) {
                status.setText("GitHub authorization was cancelled. You can submit the report again.");
            }
        });

        Thread thread = new Thread(task, "pindb-bug-report");
        thread.setDaemon(true);
        thread.start();
    }

    private void showAuthorization(GitHubAuthService.DeviceAuthorization authorization) {
        closeAuthorizationDialog();

        Label instructions = new Label("Open the GitHub authorization page, enter the code, and approve PinDB. "
                + "This window will close automatically after authorization succeeds.");
        instructions.setWrapText(true);
        TextField code = new TextField(authorization.userCode());
        code.setEditable(false);
        TextField address = new TextField(authorization.verificationUri().toString());
        address.setEditable(false);
        VBox content = new VBox(10, instructions, new Label("Device code:"), code,
                new Label("Authorization page:"), address);

        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Authorize PinDB on GitHub");
        dialog.setHeaderText("Authorize PinDB using this GitHub device code");
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefSize(620, 300);

        ButtonType openType = new ButtonType("Open GitHub", ButtonBar.ButtonData.OTHER);
        ButtonType copyType = new ButtonType("Copy Code", ButtonBar.ButtonData.OTHER);
        ButtonType cancelType = new ButtonType("Cancel Authorization", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().setAll(openType, copyType, cancelType);

        Button openButton = (Button) dialog.getDialogPane().lookupButton(openType);
        openButton.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            openExternalLink(authorization.verificationUri(),
                    "Opening GitHub… Complete authorization with code " + authorization.userCode() + ".");
        });

        Button copyButton = (Button) dialog.getDialogPane().lookupButton(copyType);
        copyButton.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            ClipboardContent clipboard = new ClipboardContent();
            clipboard.putString(authorization.userCode());
            Clipboard.getSystemClipboard().setContent(clipboard);
            status.setText("GitHub device code copied. Complete authorization in your browser.");
            code.requestFocus();
            code.selectAll();
        });

        dialog.getDialogPane().sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene != null) {
                UiUtil.applyStyles(newScene, settings);
            }
        });
        dialog.setOnHidden(event -> {
            boolean closedByUser = !closingAuthorizationProgrammatically;
            authorizationDialog = null;
            closingAuthorizationProgrammatically = false;
            if (closedByUser) {
                cancelActiveSubmission();
            }
        });

        authorizationDialog = dialog;
        dialog.show();
        code.requestFocus();
        code.selectAll();
    }

    private void showSubmissionSuccess(BugReportService.SubmittedIssue issue) {
        Label message = new Label("Your bug report was submitted successfully.");
        message.setWrapText(true);
        Label number = new Label("GitHub issue #" + issue.number() + " was created.");
        number.setStyle("-fx-font-weight: bold;");
        TextField address = new TextField(issue.url().toString());
        address.setEditable(false);
        VBox content = new VBox(10, message, number, new Label("Issue address:"), address);
        content.setPadding(new Insets(8));

        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Bug Report Submitted");
        dialog.setHeaderText("GitHub issue #" + issue.number() + " was created");
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefSize(620, 260);
        ButtonType openType = new ButtonType("Open Issue", ButtonBar.ButtonData.OTHER);
        dialog.getDialogPane().getButtonTypes().setAll(openType, ButtonType.CLOSE);
        dialog.getDialogPane().sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene != null) {
                UiUtil.applyStyles(newScene, settings);
            }
        });

        Button openButton = (Button) dialog.getDialogPane().lookupButton(openType);
        openButton.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            dialog.close();
            ExternalLinkService.openAsync(issue.url()).thenAccept(opened -> {
                if (!opened) {
                    Platform.runLater(() -> UiUtil.warning(owner, "Could Not Open Issue",
                            "PinDB could not open the web browser automatically.\n\n" + issue.url()));
                }
            });
        });
        dialog.showAndWait();
    }

    private void openExternalLink(URI uri, String progressMessage) {
        status.setText(progressMessage);
        ExternalLinkService.openAsync(uri).thenAccept(opened -> Platform.runLater(() -> {
            if (!isShowing()) {
                return;
            }
            if (opened) {
                status.setText("Waiting for GitHub authorization…");
            } else {
                status.setText("Could not open the browser automatically. Use this address manually: " + uri);
            }
        }));
    }

    private void closeAuthorizationDialog() {
        Dialog<Void> dialog = authorizationDialog;
        if (dialog == null) {
            return;
        }
        if (dialog.isShowing()) {
            closingAuthorizationProgrammatically = true;
            dialog.close();
        } else {
            authorizationDialog = null;
            closingAuthorizationProgrammatically = false;
        }
    }

    private void cancelActiveSubmission() {
        Task<BugReportService.SubmittedIssue> task = activeSubmission;
        if (task != null && task.isRunning()) {
            task.cancel(true);
        }
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
