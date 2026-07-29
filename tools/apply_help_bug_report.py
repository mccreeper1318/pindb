from pathlib import Path


def replace(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"Expected block not found in {path}: {old[:160]}")
    file.write_text(text.replace(old, new, 1), encoding="utf-8")


replace(
    "src/main/java/org/pindb/ui/DatabaseWindow.java",
    '''        help.getItems().addAll(
                item("PinDB Help", event -> new HelpDialog(stage, context.settings()).showAndWait()),
                item("Check for Updates", event -> context.checkForUpdates(stage, true)),
                item("About PinDB", event -> new AboutDialog(stage, context.settings()).showAndWait()));
''',
    '''        help.getItems().addAll(
                item("PinDB Help", event -> new HelpDialog(stage, context.settings()).showAndWait()),
                item("Check for Updates", event -> context.checkForUpdates(stage, true)),
                new javafx.scene.control.SeparatorMenuItem(),
                item("Report a Bug…", event -> new BugReportDialog(stage, context.settings()).showAndWait()),
                item("About PinDB", event -> new AboutDialog(stage, context.settings()).showAndWait()));
'''
)

replace(
    "src/main/java/org/pindb/ui/LauncherWindow.java",
    '''        Button help = new Button("Help");
        Button about = new Button("About");
''',
    '''        Button help = new Button("Help");
        Button reportBug = new Button("Report Bug");
        Button about = new Button("About");
'''
)
replace(
    "src/main/java/org/pindb/ui/LauncherWindow.java",
    '''        help.setOnAction(event -> new HelpDialog(stage, context.settings()).showAndWait());
        about.setOnAction(event -> new AboutDialog(stage, context.settings()).showAndWait());
''',
    '''        help.setOnAction(event -> new HelpDialog(stage, context.settings()).showAndWait());
        reportBug.setOnAction(event -> new BugReportDialog(stage, context.settings()).showAndWait());
        about.setOnAction(event -> new AboutDialog(stage, context.settings()).showAndWait());
'''
)
replace(
    "src/main/java/org/pindb/ui/LauncherWindow.java",
    '''        HBox footer = new HBox(8, updateStatus, spacer, checkUpdates, settings, help, about, close);
''',
    '''        HBox footer = new HBox(8, updateStatus, spacer, checkUpdates, settings, help, reportBug, about, close);
'''
)

replace(
    "build.gradle",
    '''processResources {
    filesMatching('org/pindb/app/version.properties') {
        expand(version: project.version)
    }
}
''',
    '''processResources {
    filesMatching('org/pindb/app/version.properties') {
        expand(version: project.version)
    }
    from('CHANGELOG.md') {
        into 'org/pindb/app'
    }
}
'''
)

replace(
    "CHANGELOG.md",
    '''- Added embedded-document support to logical database backup snapshots.
''',
    '''- Added embedded-document support to logical database backup snapshots.
- Added a clickable update-history browser under **Help → PinDB Help → Updates**, with online refresh, local caching, and bundled offline release notes.
- Added an in-app bug reporter that creates labeled issues in the PinDB GitHub repository using GitHub device authorization.
- Added privacy-conscious diagnostics that exclude database contents, embedded documents, filenames, and personal paths.
- Added secure GitHub authorization storage through the Linux keyring when available, with an owner-only credential-file fallback.
'''
)
