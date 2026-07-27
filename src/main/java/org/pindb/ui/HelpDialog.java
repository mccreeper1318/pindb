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
        setHeaderText("PinDB 0.1 User Guide");
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

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
                        
                        PinDB 0.1 supports text, multiline text, numbers, currency, dates, date and time, Yes/No, and dropdown fields.
                        
                        Numeric and currency fields can show one summary: **Sum**, **Average**, **Minimum**, **Maximum**, or **Entry Count**. Other supported fields can show Entry Count.
                        
                        Fields can be changed from **Edit → Manage Fields**. Deleting a field deletes its stored values after a warning. Restore an earlier internal backup when necessary.
                        """),
                tab("Backups and Trash", """
                        # Backups and Trash
                        
                        Deleted entries go to **Recently Deleted** instead of disappearing immediately. They can be restored or permanently removed.
                        
                        Each `.pindb` file contains timestamped logical backups. PinDB keeps the newest 10 by default. Future schema upgrades also create an untouched external copy before migration.
                        """),
                tab("Printing and CSV", """
                        # Printing and CSV
                        
                        Use **File → Print** to select fields, portrait or landscape orientation, and a columns or rows layout. Column headings repeat on each printed page.
                        
                        Use **File → Export CSV** to open the database in LibreOffice Calc or other spreadsheet software.
                        """),
                tab("Updates", """
                        # Updates
                        
                        PinDB checks GitHub Releases in `mccreeper1318/pindb`. Release tags may be `0.1`, `v0.1`, or `v.0.1`.
                        
                        Updates always require approval. A matching `.deb` asset is downloaded and its SHA-256 checksum is verified when the release provides one. Linux asks for the administrator password before installation. PinDB then closes, installs the update, removes temporary files, reopens, and displays formatted release notes.
                        
                        Pre-release updates are disabled by default and can be enabled in Settings.
                        """));
        tabs.setPrefSize(780, 580);
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
