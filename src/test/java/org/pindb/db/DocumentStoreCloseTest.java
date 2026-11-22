package org.pindb.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pindb.model.DatabaseView;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Test
    void busyCheckpointIsBestEffortAndStillClosesStore() {
        AtomicInteger checkpointAttempts = new AtomicInteger();
        AtomicInteger closeAttempts = new AtomicInteger();
        DocumentStore store = new DocumentStore(busyCheckpointConnection(checkpointAttempts, closeAttempts));

        assertDoesNotThrow(store::close);
        assertEquals(1, checkpointAttempts.get());
        assertEquals(1, closeAttempts.get());

        assertDoesNotThrow(store::close);
        assertEquals(1, checkpointAttempts.get());
        assertEquals(1, closeAttempts.get());
    }

    @Test
    void checkpointSqlFailureRemainsRetryableUntilCheckpointSucceeds() {
        AtomicInteger checkpointAttempts = new AtomicInteger();
        AtomicInteger closeAttempts = new AtomicInteger();
        DocumentStore store = new DocumentStore(sqlFailureThenSuccessConnection(checkpointAttempts, closeAttempts));

        assertThrows(DatabaseException.class, store::close);
        assertEquals(1, checkpointAttempts.get());
        assertEquals(0, closeAttempts.get());

        assertDoesNotThrow(store::close);
        assertEquals(2, checkpointAttempts.get());
        assertEquals(1, closeAttempts.get());

        assertDoesNotThrow(store::close);
        assertEquals(2, checkpointAttempts.get());
        assertEquals(1, closeAttempts.get());
    }

    private static Connection busyCheckpointConnection(AtomicInteger checkpointAttempts,
                                                       AtomicInteger closeAttempts) {
        return connectionProxy(() -> checkpointStatement(1, checkpointAttempts), closeAttempts);
    }

    private static Connection sqlFailureThenSuccessConnection(AtomicInteger checkpointAttempts,
                                                              AtomicInteger closeAttempts) {
        return connectionProxy(() -> sqlFailureThenSuccessStatement(checkpointAttempts), closeAttempts);
    }

    private static Connection connectionProxy(StatementFactory statementFactory,
                                              AtomicInteger closeAttempts) {
        return (Connection) Proxy.newProxyInstance(
                DocumentStoreCloseTest.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "createStatement" -> statementFactory.create();
                    case "close" -> {
                        closeAttempts.incrementAndGet();
                        yield null;
                    }
                    default -> throw new UnsupportedOperationException("Unexpected Connection method: " + method.getName());
                });
    }

    private static Statement checkpointStatement(int resultCode, AtomicInteger checkpointAttempts) {
        return (Statement) Proxy.newProxyInstance(
                DocumentStoreCloseTest.class.getClassLoader(),
                new Class<?>[]{Statement.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "executeQuery" -> checkpointResult(resultCode, checkpointAttempts);
                    case "close" -> null;
                    default -> throw new UnsupportedOperationException("Unexpected Statement method: " + method.getName());
                });
    }

    private static Statement sqlFailureThenSuccessStatement(AtomicInteger checkpointAttempts) {
        return (Statement) Proxy.newProxyInstance(
                DocumentStoreCloseTest.class.getClassLoader(),
                new Class<?>[]{Statement.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "executeQuery" -> {
                        if (checkpointAttempts.incrementAndGet() == 1) {
                            throw new SQLException("checkpoint execution failed");
                        }
                        yield checkpointResult(0, null);
                    }
                    case "close" -> null;
                    default -> throw new UnsupportedOperationException("Unexpected Statement method: " + method.getName());
                });
    }

    private static ResultSet checkpointResult(int resultCode, AtomicInteger checkpointAttempts) {
        boolean[] beforeFirst = {true};
        return (ResultSet) Proxy.newProxyInstance(
                DocumentStoreCloseTest.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "next" -> {
                        boolean hasRow = beforeFirst[0];
                        beforeFirst[0] = false;
                        yield hasRow;
                    }
                    case "getInt" -> {
                        if (checkpointAttempts != null) {
                            checkpointAttempts.incrementAndGet();
                        }
                        yield resultCode;
                    }
                    case "close" -> null;
                    default -> throw new UnsupportedOperationException("Unexpected ResultSet method: " + method.getName());
                });
    }

    @FunctionalInterface
    private interface StatementFactory {
        Statement create() throws SQLException;
    }
}
