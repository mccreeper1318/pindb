package org.pindb.util;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CloseUtilTest {
    @Test
    void closeAllAttemptsEveryResourceAndAggregatesFailures() {
        AtomicInteger closed = new AtomicInteger();
        IllegalStateException first = new IllegalStateException("first");
        IllegalArgumentException second = new IllegalArgumentException("second");

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> CloseUtil.closeAll(
                () -> {
                    closed.incrementAndGet();
                    throw first;
                },
                closed::incrementAndGet,
                () -> {
                    closed.incrementAndGet();
                    throw second;
                }));

        assertSame(first, thrown);
        assertEquals(3, closed.get());
        assertEquals(1, thrown.getSuppressed().length);
        assertSame(second, thrown.getSuppressed()[0]);
    }

    @Test
    void closeAllIsSafeForEmptyAndNullResources() {
        CloseUtil.closeAll();
        CloseUtil.closeAll((AutoCloseable[]) null);
        CloseUtil.closeAll(null, () -> { });
    }
}
