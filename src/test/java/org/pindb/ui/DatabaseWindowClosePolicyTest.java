package org.pindb.ui;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatabaseWindowClosePolicyTest {
    @Test
    void documentCloseFailureLeavesDatabaseAndWindowUntouched() {
        AtomicInteger databaseCloses = new AtomicInteger();
        AtomicInteger teardowns = new AtomicInteger();
        IllegalStateException failure = new IllegalStateException("checkpoint failed");

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> DatabaseWindow.closeWindowResources(
                        () -> { throw failure; },
                        databaseCloses::incrementAndGet,
                        teardowns::incrementAndGet));

        assertSame(failure, thrown);
        assertEquals(0, databaseCloses.get());
        assertEquals(0, teardowns.get());
    }

    @Test
    void databaseCloseFailureStillCompletesWindowTeardown() {
        AtomicInteger documentCloses = new AtomicInteger();
        AtomicInteger teardowns = new AtomicInteger();
        IllegalStateException failure = new IllegalStateException("database close failed");

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> DatabaseWindow.closeWindowResources(
                        documentCloses::incrementAndGet,
                        () -> { throw failure; },
                        teardowns::incrementAndGet));

        assertSame(failure, thrown);
        assertEquals(1, documentCloses.get());
        assertEquals(1, teardowns.get());
    }
}
