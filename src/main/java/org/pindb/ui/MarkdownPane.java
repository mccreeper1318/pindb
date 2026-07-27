package org.pindb.ui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.util.ArrayList;
import java.util.List;

/** Renders the Markdown subset commonly used in GitHub release notes. */
public final class MarkdownPane extends ScrollPane {
    private final VBox content = new VBox(8);

    public MarkdownPane(String markdown) {
        setFitToWidth(true);
        setPannable(true);
        content.setPadding(new Insets(12));
        setContent(content);
        render(markdown == null ? "" : markdown);
    }

    public void render(String markdown) {
        content.getChildren().clear();
        String[] lines = markdown.replace("\r\n", "\n").split("\n", -1);
        boolean inCodeBlock = false;
        StringBuilder code = new StringBuilder();
        for (String line : lines) {
            if (line.trim().startsWith("```")) {
                if (inCodeBlock) {
                    Label label = new Label(code.toString());
                    label.setWrapText(true);
                    label.getStyleClass().add("markdown-code");
                    label.setMaxWidth(Double.MAX_VALUE);
                    content.getChildren().add(label);
                    code.setLength(0);
                }
                inCodeBlock = !inCodeBlock;
                continue;
            }
            if (inCodeBlock) {
                code.append(line).append('\n');
                continue;
            }
            if (line.isBlank()) {
                content.getChildren().add(new Label(""));
            } else if (line.startsWith("### ")) {
                content.getChildren().add(heading(line.substring(4), "markdown-h3"));
            } else if (line.startsWith("## ")) {
                content.getChildren().add(heading(line.substring(3), "markdown-h2"));
            } else if (line.startsWith("# ")) {
                content.getChildren().add(heading(line.substring(2), "markdown-h1"));
            } else if (line.matches("^\\s*[-*+]\\s+.*")) {
                String text = line.replaceFirst("^\\s*[-*+]\\s+", "");
                TextFlow flow = inline("• " + text);
                flow.setPadding(new Insets(0, 0, 0, 14));
                content.getChildren().add(flow);
            } else if (line.matches("^\\s*\\d+\\.\\s+.*")) {
                content.getChildren().add(inline(line.trim()));
            } else if (line.startsWith("> ")) {
                TextFlow flow = inline(line.substring(2));
                flow.setStyle("-fx-border-color: transparent transparent transparent #168f8b; -fx-border-width: 0 0 0 3; -fx-padding: 4 0 4 10;");
                content.getChildren().add(flow);
            } else if (line.matches("^[-*_]{3,}$")) {
                javafx.scene.control.Separator separator = new javafx.scene.control.Separator();
                content.getChildren().add(separator);
            } else {
                content.getChildren().add(inline(line));
            }
        }
        if (code.length() > 0) {
            Label label = new Label(code.toString());
            label.getStyleClass().add("markdown-code");
            content.getChildren().add(label);
        }
    }

    private Node heading(String text, String styleClass) {
        Label label = new Label(stripLinks(text));
        label.setWrapText(true);
        label.getStyleClass().add(styleClass);
        return label;
    }

    private TextFlow inline(String source) {
        TextFlow flow = new TextFlow();
        flow.setLineSpacing(2);
        flow.getChildren().addAll(parseInline(source));
        return flow;
    }

    private List<Text> parseInline(String source) {
        List<Text> result = new ArrayList<>();
        String text = stripLinks(source);
        int position = 0;
        while (position < text.length()) {
            int bold = text.indexOf("**", position);
            int code = text.indexOf('`', position);
            int next;
            boolean isBold;
            if (bold >= 0 && (code < 0 || bold < code)) {
                next = bold;
                isBold = true;
            } else if (code >= 0) {
                next = code;
                isBold = false;
            } else {
                result.add(new Text(text.substring(position)));
                break;
            }
            if (next > position) {
                result.add(new Text(text.substring(position, next)));
            }
            if (isBold) {
                int end = text.indexOf("**", next + 2);
                if (end < 0) {
                    result.add(new Text(text.substring(next)));
                    break;
                }
                Text item = new Text(text.substring(next + 2, end));
                item.setFont(Font.font(item.getFont().getFamily(), FontWeight.BOLD, item.getFont().getSize()));
                result.add(item);
                position = end + 2;
            } else {
                int end = text.indexOf('`', next + 1);
                if (end < 0) {
                    result.add(new Text(text.substring(next)));
                    break;
                }
                Text item = new Text(text.substring(next + 1, end));
                item.setStyle("-fx-font-family: monospace; -fx-fill: #087f7b;");
                result.add(item);
                position = end + 1;
            }
        }
        return result;
    }

    private static String stripLinks(String value) {
        return value.replaceAll("!\\[([^]]*)]\\([^)]+\\)", "$1")
                .replaceAll("\\[([^]]+)]\\([^)]+\\)", "$1");
    }
}
