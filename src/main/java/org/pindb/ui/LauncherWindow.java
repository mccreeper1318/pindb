package org.pindb.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.pindb.AppContext;
import org.pindb.AppVersion;
import org.pindb.db.DatabaseService;
import org.pindb.service.CsvService;
import org.pindb.service.TemplateService;

import java.io.File;
import java.nio.file.Path;
import java.util.Objects;

public final class LauncherWindow {
    private final AppContext context;
    private final Stage stage = new Stage();
    private final ObservableList<Path> recentFiles = FXCollections.observableArrayList();
    private final ListView<Path> recentList = new ListView<>(recentFiles);
    private final Label updateStatus = new Label("PinDB " + AppVersion.VERSION);
    private final Scene scene;

    public LauncherWindow(AppContext context) {
        this.context = context;
        stage.setTitle("PinDB");
        stage.getIcons().add(icon());

        ImageView logo = new ImageView(icon());
        logo.setFitWidth(72);
        logo.setFitHeight(72);
        logo.setPreserveRatio(true);
        VBox titleBox = new VBox(3, UiUtil.title("PinDB"),
                subtitle("Create, organize, search, print, and export portable personal databases."));
        HBox header = new HBox(16, logo, titleBox);
        header.setAlignment(Pos.CENTER_LEFT);

        Button create = UiUtil.primaryButton("Create New Database");
        Button open = new Button("Open Database");
        Button importCsv = new Button("Import CSV");
        Button restore = new Button("Restore Backup");
        MenuButton templates = new MenuButton("Database Templates");
        for (TemplateService.Template template : TemplateService.Template.values()) {
            if (template == TemplateService.Template.BLANK) {
                continue;
            }
            MenuItem item = new MenuItem(template.toString());
            item.setOnAction(event -> createDatabase(template));
            templates.getItems().add(item);
        }

        GridPane actionGrid = new GridPane();
        actionGrid.setHgap(10);
        actionGrid.setVgap(10);
        actionGrid.add(create, 0, 0);
        actionGrid.add(open, 1, 0);
        actionGrid.add(importCsv, 0, 1);
        actionGrid.add(restore, 1, 1);
        actionGrid.add(templates, 0, 2, 2, 1);
        for (javafx.scene.Node node : actionGrid.getChildren()) {
            if (node instanceof javafx.scene.control.Control control) {
                control.setMaxWidth(Double.MAX_VALUE);
            }
        }
        GridPane.setHgrow(create, Priority.ALWAYS);
        GridPane.setHgrow(open, Priority.ALWAYS);

        VBox actionCard = new VBox(12, section("Get Started"), actionGrid);
        actionCard.getStyleClass().add("launcher-card");

        recentList.setPlaceholder(subtitle("No recent databases yet."));
        recentList.setCellFactory(list -> new RecentFileCell());
        recentList.setPrefHeight(260);
        recentList.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && recentList.getSelectionModel().getSelectedItem() != null) {
                context.openDatabase(recentList.getSelectionModel().getSelectedItem());
            }
        });
        Button openRecent = new Button("Open Selected");
        Button removeRecent = new Button("Remove from List");
        openRecent.disableProperty().bind(recentList.getSelectionModel().selectedItemProperty().isNull());
        removeRecent.disableProperty().bind(openRecent.disableProperty());
        openRecent.setOnAction(event -> context.openDatabase(recentList.getSelectionModel().getSelectedItem()));
        removeRecent.setOnAction(event -> {
            Path path = recentList.getSelectionModel().getSelectedItem();
            if (path != null) {
                context.settings().removeRecentFile(path);
                refreshRecentFiles();
            }
        });
        HBox recentActions = new HBox(8, openRecent, removeRecent);
        VBox recentCard = new VBox(10, section("Recent Databases"), recentList, recentActions);
        recentCard.getStyleClass().add("launcher-card");
        VBox.setVgrow(recentList, Priority.ALWAYS);

        Button checkUpdates = new Button("Check for Updates");
        Button settings = new Button("Settings");
        Button help = new Button("Help");
        Button reportBug = new Button("Report Bug");
        Button about = new Button("About");
        Button close = new Button("Exit PinDB");
        checkUpdates.setOnAction(event -> context.checkForUpdates(stage, true));
        settings.setOnAction(event -> new SettingsDialog(stage, context.settings()).showAndWait().ifPresent(saved -> {
            if (saved) {
                context.refreshStyles();
            }
        }));
        help.setOnAction(event -> new HelpDialog(stage, context.settings()).showAndWait());
        reportBug.setOnAction(event -> new BugReportDialog(stage, context.settings()).showAndWait());
        about.setOnAction(event -> new AboutDialog(stage, context.settings()).showAndWait());
        close.setOnAction(event -> context.closeAll());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        updateStatus.getStyleClass().add("muted-label");
        HBox footer = new HBox(8, updateStatus, spacer, checkUpdates, settings, help, reportBug, about, close);
        footer.setAlignment(Pos.CENTER_LEFT);

        VBox center = new VBox(16, header, actionCard, recentCard);
        VBox.setVgrow(recentCard, Priority.ALWAYS);
        BorderPane root = new BorderPane(center);
        root.setPadding(new Insets(22));
        root.setBottom(new VBox(14, new Separator(), footer));
        BorderPane.setMargin(root.getBottom(), new Insets(16, 0, 0, 0));

        scene = new Scene(root, 850, 760);
        UiUtil.applyStyles(scene, context.settings());
        stage.setScene(scene);
        stage.setMinWidth(720);
        stage.setMinHeight(620);
        stage.setOnCloseRequest(event -> {
            event.consume();
            context.closeAll();
        });

        create.setOnAction(event -> createDatabase(TemplateService.Template.BLANK));
        open.setOnAction(event -> chooseDatabase());
        importCsv.setOnAction(event -> importCsv());
        restore.setOnAction(event -> restoreBackup());
        refreshRecentFiles();
    }

    public Stage stage() {
        return stage;
    }

    public void showAndFocus() {
        stage.show();
        stage.toFront();
        stage.requestFocus();
    }

    public void hide() {
        stage.hide();
    }

    public void refreshStyle() {
        UiUtil.applyStyles(scene, context.settings());
    }

    public void refreshRecentFiles() {
        recentFiles.setAll(context.settings().recentFiles());
    }

    public void setUpdateStatus(String status) {
        updateStatus.setText(status);
    }

    private void createDatabase(TemplateService.Template template) {
        DatabaseWizard.show(stage, context.settings(), template).ifPresent(context::openDatabase);
    }

    private void chooseDatabase() {
        FileChooser chooser = databaseChooser("Open PinDB Database");
        File file = chooser.showOpenDialog(stage);
        if (file != null) {
            context.openDatabase(file.toPath());
        }
    }

    private void importCsv() {
        FileChooser sourceChooser = new FileChooser();
        sourceChooser.setTitle("Choose CSV File");
        sourceChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        File source = sourceChooser.showOpenDialog(stage);
        if (source == null) {
            return;
        }
        String defaultName = source.getName().replaceFirst("(?i)\\.csv$", "");
        TextInputDialog nameDialog = new TextInputDialog(defaultName);
        nameDialog.initOwner(stage);
        nameDialog.setTitle("Import CSV");
        nameDialog.setHeaderText("Name the imported database");
        String name = nameDialog.showAndWait().map(String::trim).filter(value -> !value.isBlank()).orElse(null);
        if (name == null) {
            return;
        }
        FileChooser destinationChooser = databaseChooser("Save Imported Database");
        destinationChooser.setInitialFileName(safeFileName(name) + ".pindb");
        File destination = destinationChooser.showSaveDialog(stage);
        if (destination == null) {
            return;
        }
        try (DatabaseService imported = CsvService.importCsv(source.toPath(), ensureExtension(destination.toPath()), name)) {
            // The database is closed before opening it through the normal window lifecycle.
        } catch (RuntimeException exception) {
            UiUtil.error(stage, "CSV Import Failed", "PinDB could not import the selected CSV file.", exception);
            return;
        }
        context.openDatabase(ensureExtension(destination.toPath()));
    }

    private void restoreBackup() {
        FileChooser chooser = databaseChooser("Choose Database Containing Backups");
        File file = chooser.showOpenDialog(stage);
        if (file == null) {
            return;
        }
        try (DatabaseService database = DatabaseService.open(file.toPath())) {
            new BackupRestoreDialog(stage, context.settings(), database, () -> { }).showAndWait();
        } catch (RuntimeException exception) {
            UiUtil.error(stage, "Could Not Restore Backup",
                    "PinDB could not open the selected database or restore its backup.", exception);
            return;
        }
        context.openDatabase(file.toPath());
    }

    private static FileChooser databaseChooser(String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("PinDB databases", "*.pindb"),
                new FileChooser.ExtensionFilter("All files", "*.*"));
        return chooser;
    }

    private static Path ensureExtension(Path path) {
        return path.getFileName().toString().toLowerCase().endsWith(".pindb")
                ? path : path.resolveSibling(path.getFileName() + ".pindb");
    }

    private static String safeFileName(String value) {
        String sanitized = value.trim().replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("^-+|-+$", "");
        return sanitized.isBlank() ? "database" : sanitized;
    }

    private static Label subtitle(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.getStyleClass().add("subtitle-label");
        return label;
    }

    private static Label section(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-title");
        return label;
    }

    private static Image icon() {
        return new Image(Objects.requireNonNull(
                LauncherWindow.class.getResourceAsStream("/org/pindb/app/icon.png")));
    }

    private final class RecentFileCell extends ListCell<Path> {
        @Override
        protected void updateItem(Path path, boolean empty) {
            super.updateItem(path, empty);
            if (empty || path == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            Label name = new Label(path.getFileName().toString());
            name.setStyle("-fx-font-weight: bold;");
            Label location = new Label(path.getParent() == null ? path.toString() : path.getParent().toString());
            location.getStyleClass().add("muted-label");
            setGraphic(new VBox(2, name, location));
            setText(null);
        }
    }
}
