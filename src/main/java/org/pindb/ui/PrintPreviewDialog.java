package org.pindb.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.List;
import java.util.Objects;

public final class PrintPreviewDialog {
    public enum Result {
        PRINT,
        BACK_TO_SETTINGS,
        CANCEL
    }

    private final Stage stage = new Stage();
    private final List<Node> pages;
    private final StackPane pageHost = new StackPane();
    private final Label pageLabel = new Label();
    private final Button previous = new Button("Previous");
    private final Button next = new Button("Next");
    private Result result = Result.CANCEL;
    private int pageIndex;

    public PrintPreviewDialog(Window owner, String databaseName, List<Node> pages) {
        this.pages = List.copyOf(Objects.requireNonNull(pages, "pages"));
        if (this.pages.isEmpty()) {
            throw new IllegalArgumentException("Print preview requires at least one page.");
        }

        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("Print Preview — " + Objects.requireNonNullElse(databaseName, "PinDB"));
        stage.setMinWidth(720);
        stage.setMinHeight(560);

        Label description = new Label(
                "Review the exact pages PinDB will send to the selected printer. "
                        + "Use Back to Settings to make changes before printing.");
        description.setWrapText(true);
        description.getStyleClass().add("subtitle-label");

        pageHost.setAlignment(Pos.TOP_CENTER);
        pageHost.setPadding(new Insets(18));
        pageHost.setStyle("-fx-background-color: #707780;");

        ScrollPane scroll = new ScrollPane(pageHost);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(false);
        scroll.setPannable(true);

        previous.setOnAction(event -> showPage(pageIndex - 1));
        next.setOnAction(event -> showPage(pageIndex + 1));
        HBox navigation = new HBox(10, previous, pageLabel, next);
        navigation.setAlignment(Pos.CENTER);

        Button back = new Button("Back to Settings");
        Button cancel = new Button("Cancel");
        Button print = UiUtil.primaryButton("Print");
        back.setOnAction(event -> finish(Result.BACK_TO_SETTINGS));
        cancel.setOnAction(event -> finish(Result.CANCEL));
        print.setOnAction(event -> finish(Result.PRINT));

        HBox actions = new HBox(10, back, cancel, print);
        actions.setAlignment(Pos.CENTER_RIGHT);

        BorderPane footer = new BorderPane();
        footer.setCenter(navigation);
        footer.setRight(actions);

        VBox root = new VBox(12, description, scroll, footer);
        root.setPadding(new Insets(14));
        VBox.setVgrow(scroll, javafx.scene.layout.Priority.ALWAYS);

        Scene scene = new Scene(root, 920, 740);
        if (owner != null && owner.getScene() != null) {
            scene.getStylesheets().setAll(owner.getScene().getStylesheets());
        }
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> result = Result.CANCEL);
        stage.setOnHidden(event -> pageHost.getChildren().clear());

        showPage(0);
    }

    public Result showAndWait() {
        stage.showAndWait();
        return result;
    }

    private void showPage(int requestedIndex) {
        pageIndex = Math.max(0, Math.min(requestedIndex, pages.size() - 1));
        Node page = pages.get(pageIndex);
        if (page.getParent() instanceof javafx.scene.layout.Pane parent) {
            parent.getChildren().remove(page);
        }
        page.applyCss();
        page.autosize();
        pageHost.getChildren().setAll(page);
        pageLabel.setText("Page " + (pageIndex + 1) + " of " + pages.size());
        previous.setDisable(pageIndex == 0);
        next.setDisable(pageIndex >= pages.size() - 1);
    }

    private void finish(Result nextResult) {
        result = nextResult;
        pageHost.getChildren().clear();
        stage.close();
    }
}
