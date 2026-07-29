package org.pindb.ui;

import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.Window;
import org.pindb.service.SettingsService;

public final class HelpDialog extends Dialog<Void> {
    public HelpDialog(Window owner, SettingsService settings) {
        initOwner(owner);
        setTitle("PinDB Help");
        setHeaderText("PinDB User Guide");
        setResizable(true);
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        Tab updates = new Tab("Updates", new UpdateHistoryPane(settings));
        updates.setClosable(false);
        TabPane tabs = new TabPane(
                tab("Getting Started", """
                        # Getting Started

                        1. Choose **Create New Database** from the launcher.
                        2. Name the database and choose where its `.pindb` file should be saved.
                        3. Add and arrange fields. Field definitions and display settings are stored inside the file.
                        4. Press **Create Database**. Use the **+** button to add entries.

                        Double-click an entry, or select it and press **Edit**, to change it.
                        """),
                tab("Fields and Summaries", """
                        # Fields and Summaries

                        PinDB supports text, multiline text, numbers, currency, dates, date and time, Yes/No, dropdown, and document fields.

                        Numeric and currency fields can show one summary: **Sum**, **Average**, **Minimum**, **Maximum**, or **Entry Count**. Other supported fields can show Entry Count.

                        Fields can be changed from **Database → Manage Fields**. Deleting a field deletes its stored values after a warning. Restore an earlier internal backup when necessary.
                        """),
                tab("Documents", """
                        # Documents

                        A **Document** field stores the original file inside the `.pindb` database. Click its filename in table or record view to open the stored copy.

                        The document viewer can preview supported PDFs, DOCX files, text files, and images. It can print supported previews, save a copy, or open the document using the system application.
                        """),
                tab("Backups and Trash", """
                        # Backups and Trash

                        Deleted entries go to **Recently Deleted** instead of disappearing immediately. They can be restored or permanently removed.

                        Each `.pindb` file contains timestamped logical backups, including embedded documents. Future schema upgrades also create an untouched external copy before migration.
                        """),
                tab("Printing and CSV", """
                        # Printing and CSV

                        Use **File → Print** to select fields, portrait or landscape orientation, and a columns or rows layout. Column headings can repeat on each printed page.

                        Use **File → Export Visible Entries to CSV** to open database information in LibreOffice Calc or other spreadsheet software.
                        """),
                updates,
                tab("Bug Reports", """
                        # Reporting a Bug

                        Use **Help → Report a Bug…** to create a structured report in the PinDB GitHub Issues page without manually creating the issue in a browser.

                        The first report requires one-time GitHub device authorization. PinDB never includes database contents, embedded documents, filenames, or personal paths automatically. Review the report fields and diagnostic option before submitting.
                        """));
        tabs.setPrefSize(900, 650);
        getDialogPane().setContent(tabs);
        getDialogPane().sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene != null) {
                UiUtil.applyStyles(newScene, settings);
            }
        });
    }

    private Tab tab(String title, String markdown) {
        Tab tab = new Tab(title, new MarkdownPane(markdown));
        tab.setClosable(false);
        return tab;
    }
}
