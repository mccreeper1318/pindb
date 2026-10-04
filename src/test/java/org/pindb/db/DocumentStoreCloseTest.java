package org.pindb.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pindb.model.DatabaseView;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class DocumentStoreCloseTest {
    @TempDir
    Path tempDirectory;

    @Test
    void closeIsIdempotentAfterSuccessfulCheckpoint() {
        Path databasePath = tempDirectory.resolve("close-test.pindb");
        DatabaseService database = DatabaseService.create(
                databasePath, "Close Test", "", List.of(), DatabaseView.TABLE, 2);
        database.close();

        DocumentStore store = new DocumentStore(databasePath);
        store.close();

        assertDoesNotThrow(store::close);
    }
}
