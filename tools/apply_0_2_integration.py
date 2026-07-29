from pathlib import Path


def replace(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"Expected source block was not found in {path}:\n{old[:240]}")
    file.write_text(text.replace(old, new, 1), encoding="utf-8")


# Database window: keep one document store open with the database window.
replace(
    "src/main/java/org/pindb/ui/DatabaseWindow.java",
    "import javafx.scene.control.Label;\n",
    "import javafx.scene.control.Label;\nimport javafx.scene.control.Hyperlink;\n",
)
replace(
    "src/main/java/org/pindb/ui/DatabaseWindow.java",
    "import org.pindb.db.DatabaseService;\n",
    "import org.pindb.db.DatabaseService;\nimport org.pindb.db.DocumentStore;\n",
)
replace(
    "src/main/java/org/pindb/ui/DatabaseWindow.java",
    "import org.pindb.model.DatabaseView;\n",
    "import org.pindb.model.DatabaseView;\nimport org.pindb.model.DocumentData;\n",
)
replace(
    "src/main/java/org/pindb/ui/DatabaseWindow.java",
    "    private final DatabaseService database;\n",
    "    private final DatabaseService database;\n    private final DocumentStore documentStore;\n",
)
replace(
    "src/main/java/org/pindb/ui/DatabaseWindow.java",
    "        this.database = database;\n        this.onClosed = onClosed;\n",
    "        this.database = database;\n        this.documentStore = new DocumentStore(database.path());\n        this.onClosed = onClosed;\n",
)
replace(
    "src/main/java/org/pindb/ui/DatabaseWindow.java",
    "        closed = true;\n        database.close();\n",
    "        closed = true;\n        documentStore.close();\n        database.close();\n",
)
replace(
    "src/main/java/org/pindb/ui/DatabaseWindow.java",
    """            EntryEditorDialog.Result result = new EntryEditorDialog(
                    stage, context.settings(), fields, null).showAndWait().orElse(null);
            if (result == null) {
                return;
            }
            try {
                database.addRecord(result.values());
                reloadAll();
                addAnother = result.addAnother();
""",
    """            EntryEditorDialog.Result result = new EntryEditorDialog(
                    stage, context.settings(), fields, null, Map.of()).showAndWait().orElse(null);
            if (result == null) {
                return;
            }
            try {
                long recordId = database.addRecord(result.values());
                documentStore.replaceDocuments(recordId, result.documents());
                reloadAll();
                addAnother = result.addAnother();
""",
)
replace(
    "src/main/java/org/pindb/ui/DatabaseWindow.java",
    """        new EntryEditorDialog(stage, context.settings(), fields, selected).showAndWait().ifPresent(result -> {
            try {
                database.updateRecord(selected.id(), result.values());
                reloadAll();
""",
    """        Map<Long, DocumentData> existingDocuments = documentStore.documentsForRecord(selected.id());
        new EntryEditorDialog(stage, context.settings(), fields, selected, existingDocuments)
                .showAndWait().ifPresent(result -> {
            try {
                database.updateRecord(selected.id(), result.values());
                documentStore.replaceDocuments(selected.id(), result.documents());
                reloadAll();
""",
)
replace(
    "src/main/java/org/pindb/ui/DatabaseWindow.java",
    """            column.setCellFactory(ignored -> new javafx.scene.control.TableCell<>() {
                @Override
                protected void updateItem(String value, boolean empty) {
                    super.updateItem(value, empty);
                    setText(empty ? null : UiUtil.formatValue(field, value));
                    setWrapText(field.type() == FieldType.MULTILINE_TEXT);
                }
            });
""",
    """            column.setCellFactory(ignored -> new javafx.scene.control.TableCell<>() {
                @Override
                protected void updateItem(String value, boolean empty) {
                    super.updateItem(value, empty);
                    setGraphic(null);
                    setText(null);
                    if (empty) {
                        return;
                    }
                    if (field.type() == FieldType.DOCUMENT && value != null && !value.isBlank()) {
                        Hyperlink link = new Hyperlink(value);
                        link.setOnAction(event -> {
                            RecordData record = getTableRow() == null ? null : getTableRow().getItem();
                            if (record != null) {
                                openDocument(record.id(), field.id());
                            }
                        });
                        setGraphic(link);
                    } else {
                        setText(UiUtil.formatValue(field, value));
                        setWrapText(field.type() == FieldType.MULTILINE_TEXT);
                    }
                }
            });
""",
)
replace(
    "src/main/java/org/pindb/ui/DatabaseWindow.java",
    """                Label value = new Label(UiUtil.formatValue(field, record.value(field.id())));
                value.setWrapText(true);
                value.setMaxWidth(Double.MAX_VALUE);
                grid.add(name, 0, row);
                grid.add(value, 1, row);
                GridPane.setHgrow(value, Priority.ALWAYS);
""",
    """                javafx.scene.Node value;
                if (field.type() == FieldType.DOCUMENT && !record.value(field.id()).isBlank()) {
                    Hyperlink link = new Hyperlink(record.value(field.id()));
                    link.setOnAction(event -> openDocument(record.id(), field.id()));
                    value = link;
                } else {
                    Label label = new Label(UiUtil.formatValue(field, record.value(field.id())));
                    label.setWrapText(true);
                    label.setMaxWidth(Double.MAX_VALUE);
                    value = label;
                }
                grid.add(name, 0, row);
                grid.add(value, 1, row);
                GridPane.setHgrow(value, Priority.ALWAYS);
""",
)
replace(
    "src/main/java/org/pindb/ui/DatabaseWindow.java",
    "    private void exportCsv() {\n",
    """    private void openDocument(long recordId, long fieldId) {
        documentStore.document(recordId, fieldId).ifPresentOrElse(
                document -> new DocumentViewer(stage, context.settings(), document).show(),
                () -> UiUtil.warning(stage, "Document Unavailable",
                        "The selected document is no longer stored in this entry."));
    }

    private void exportCsv() {
""",
)

# Internal backups restore embedded documents after the normal logical snapshot.
replace(
    "src/main/java/org/pindb/ui/BackupRestoreDialog.java",
    "import org.pindb.db.DatabaseService;\n",
    "import org.pindb.db.DatabaseService;\nimport org.pindb.db.DocumentStore;\n",
)
replace(
    "src/main/java/org/pindb/ui/BackupRestoreDialog.java",
    """                database.restoreSnapshot(selected.id());
                reload();
""",
    """                database.restoreSnapshot(selected.id());
                try (DocumentStore documents = new DocumentStore(database.path())) {
                    documents.restoreSnapshot(selected.id());
                }
                reload();
""",
)

# Document fields do not use text defaults, uniqueness, limits, or summaries.
replace(
    "src/main/java/org/pindb/ui/FieldEditorDialog.java",
    """        boolean text = selected == FieldType.TEXT || selected == FieldType.MULTILINE_TEXT;
        boolean dropdown = selected == FieldType.DROPDOWN;

        minimum.setDisable(!numeric);
        maximum.setDisable(!numeric);
        useCurrentDate.setDisable(!date);
        defaultValue.setDisable(date && useCurrentDate.isSelected());
        characterLimit.setDisable(!text);
        dropdownOptions.setDisable(!dropdown);
""",
    """        boolean text = selected == FieldType.TEXT || selected == FieldType.MULTILINE_TEXT;
        boolean dropdown = selected == FieldType.DROPDOWN;
        boolean document = selected == FieldType.DOCUMENT;

        minimum.setDisable(!numeric);
        maximum.setDisable(!numeric);
        useCurrentDate.setDisable(!date);
        defaultValue.setDisable(document || (date && useCurrentDate.isSelected()));
        unique.setDisable(document);
        characterLimit.setDisable(!text);
        dropdownOptions.setDisable(!dropdown);
""",
)
replace(
    "src/main/java/org/pindb/ui/FieldEditorDialog.java",
    """        result.setDefaultValue(useCurrentDate.isSelected()
                ? (type.getValue() == FieldType.DATE ? "${TODAY}" : "${NOW}")
                : defaultValue.getText().trim());
""",
    """        result.setDefaultValue(type.getValue() == FieldType.DOCUMENT ? ""
                : useCurrentDate.isSelected()
                ? (type.getValue() == FieldType.DATE ? "${TODAY}" : "${NOW}")
                : defaultValue.getText().trim());
""",
)

# Printing: estimate using the exact rendered column width and paginate summaries.
replace(
    "src/main/java/org/pindb/service/PrintService.java",
    "        double columnWidth = Math.max(70, layout.getPrintableWidth() / Math.max(1, fields.size()));\n",
    "        double columnWidth = printableColumnWidth(layout, fields.size());\n",
)
replace(
    "src/main/java/org/pindb/service/PrintService.java",
    "            GridPane grid = tableGrid(fields);\n",
    "            GridPane grid = tableGrid(fields, columnWidth);\n",
)
replace(
    "src/main/java/org/pindb/service/PrintService.java",
    """    private static GridPane tableGrid(List<FieldDefinition> fields) {
        GridPane grid = new GridPane();
        grid.setGridLinesVisible(true);
        grid.setMaxWidth(Double.MAX_VALUE);
        double percent = fields.isEmpty() ? 100 : 100.0 / fields.size();
        for (int i = 0; i < Math.max(1, fields.size()); i++) {
            ColumnConstraints constraints = new ColumnConstraints();
            constraints.setPercentWidth(percent);
            constraints.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(constraints);
        }
        return grid;
    }
""",
    """    private static GridPane tableGrid(List<FieldDefinition> fields, double columnWidth) {
        GridPane grid = new GridPane();
        grid.setGridLinesVisible(true);
        int count = Math.max(1, fields.size());
        grid.setMinWidth(columnWidth * count);
        grid.setPrefWidth(columnWidth * count);
        grid.setMaxWidth(columnWidth * count);
        for (int i = 0; i < count; i++) {
            ColumnConstraints constraints = new ColumnConstraints(columnWidth, columnWidth, columnWidth);
            constraints.setHgrow(Priority.NEVER);
            grid.getColumnConstraints().add(constraints);
        }
        return grid;
    }
""",
)
replace(
    "src/main/java/org/pindb/service/PrintService.java",
    """    private static void appendSummaryBody(List<Node> bodies, List<FieldDefinition> fields,
                                          List<RecordData> records, PrintOptions options, PageLayout layout) {
        Map<FieldDefinition, String> summaries = summaries(fields, records);
        if (summaries.isEmpty()) {
            return;
        }
        VBox summary = new VBox(6);
        Label title = new Label("Field Summaries");
        title.setFont(Font.font(15));
        title.setStyle("-fx-font-weight: bold; -fx-text-fill: black;");
        summary.getChildren().add(title);
        for (Map.Entry<FieldDefinition, String> entry : summaries.entrySet()) {
            Label line = new Label(entry.getKey().name() + " — "
                    + entry.getKey().summaryType().displayName() + ": " + entry.getValue());
            line.setWrapText(true);
            line.setStyle("-fx-text-fill: black;");
            summary.getChildren().add(line);
        }
        double estimated = 34 + summaries.size() * 24.0;
        if (estimated > availableBodyHeight(options, layout)) {
            summary.setScaleX(0.9);
            summary.setScaleY(0.9);
        }
        bodies.add(summary);
    }
""",
    """    private static void appendSummaryBody(List<Node> bodies, List<FieldDefinition> fields,
                                          List<RecordData> records, PrintOptions options, PageLayout layout) {
        List<Map.Entry<FieldDefinition, String>> entries = new ArrayList<>(summaries(fields, records).entrySet());
        if (entries.isEmpty()) {
            return;
        }
        double bodyHeight = availableBodyHeight(options, layout);
        double lineWidth = Math.max(80, layout.getPrintableWidth() - 16);
        int index = 0;
        int summaryPage = 1;
        while (index < entries.size()) {
            VBox summary = new VBox(6);
            Label title = new Label(summaryPage == 1 ? "Field Summaries" : "Field Summaries (continued)");
            title.setFont(Font.font(15));
            title.setStyle("-fx-font-weight: bold; -fx-text-fill: black;");
            summary.getChildren().add(title);
            double used = 34;
            while (index < entries.size()) {
                Map.Entry<FieldDefinition, String> entry = entries.get(index);
                String text = entry.getKey().name() + " — "
                        + entry.getKey().summaryType().displayName() + ": " + entry.getValue();
                double lineHeight = estimateTextHeight(text, lineWidth) + 6;
                if (summary.getChildren().size() > 1 && used + lineHeight > bodyHeight) {
                    break;
                }
                Label line = new Label(text);
                line.setWrapText(true);
                line.setMaxWidth(lineWidth);
                line.setStyle("-fx-text-fill: black;");
                summary.getChildren().add(line);
                used += Math.min(lineHeight, bodyHeight);
                index++;
                if (used >= bodyHeight) {
                    break;
                }
            }
            bodies.add(summary);
            summaryPage++;
        }
    }
""",
)
replace(
    "src/main/java/org/pindb/service/PrintService.java",
    "    private static double availableBodyHeight(PrintOptions options, PageLayout layout) {\n",
    """    static double printableColumnWidth(PageLayout layout, int fieldCount) {
        return Math.max(1, (layout.getPrintableWidth() - 8) / Math.max(1, fieldCount));
    }

    private static double availableBodyHeight(PrintOptions options, PageLayout layout) {
""",
)

# Start a conventional 0.2 development changelog section.
changelog = Path("CHANGELOG.md")
text = changelog.read_text(encoding="utf-8")
heading = """## 0.2-dev

### Added

- Added a **Document** field type that stores the original file inside the `.pindb` SQLite database.
- Added clickable document filenames in table and record views.
- Added an in-app viewer for PDF, DOCX, text, and common image files.
- Added printing, Save Copy, and system-application actions to the document viewer.
- Added embedded-document support to logical database backup snapshots.

### Fixed

- Fixed CSV imports keeping display-formatted dates instead of normalizing them to PinDB's internal date format (Issue #12).
- Fixed typed DatePicker values being replaced by the previous or current date when Enter saved an entry (Issue #13).
- Fixed large groups of printed summaries being clipped instead of continuing onto additional pages (Issue #16).
- Fixed table-print pagination estimating wrapping with a different column width than the rendered table (Issue #17).

"""
if "## 0.2-dev" not in text:
    text = text.replace("# Changelog\n\n", "# Changelog\n\n" + heading, 1)
    changelog.write_text(text, encoding="utf-8")
