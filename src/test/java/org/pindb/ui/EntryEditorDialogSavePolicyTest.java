package org.pindb.ui;

import org.junit.jupiter.api.Test;
import org.pindb.db.DatabaseException;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntryEditorDialogSavePolicyTest {

    @Test
    void failedDatabaseSaveKeepsSaveAttemptIncompleteAndReportsValidationMessage() {
        EntryEditorDialog.Result result = new EntryEditorDialog.Result(
                Map.of(1L, "duplicate"), Map.of(), false);
        AtomicReference<String> error = new AtomicReference<>("");

        boolean saved = EntryEditorDialog.attemptSave(
                ignored -> {
                    throw new DatabaseException("Name must be unique. This value is already in use.");
                },
                result,
                error::set);

        assertFalse(saved);
        assertEquals("Name must be unique. This value is already in use.", error.get());
    }

    @Test
    void successfulDatabaseSaveAllowsDialogToComplete() {
        EntryEditorDialog.Result result = new EntryEditorDialog.Result(
                Map.of(1L, "valid"), Map.of(), false);
        AtomicReference<EntryEditorDialog.Result> persisted = new AtomicReference<>();

        boolean saved = EntryEditorDialog.attemptSave(persisted::set, result, ignored -> {
        });

        assertTrue(saved);
        assertEquals(result, persisted.get());
    }
}
