package org.pindb.util;

public final class CloseUtil {
    private CloseUtil() {
    }

    public static void closeAll(AutoCloseable... closeables) {
        if (closeables == null || closeables.length == 0) {
            return;
        }

        RuntimeException failure = null;
        for (AutoCloseable closeable : closeables) {
            if (closeable == null) {
                continue;
            }
            try {
                closeable.close();
            } catch (RuntimeException exception) {
                failure = merge(failure, exception);
            } catch (Exception exception) {
                failure = merge(failure, new IllegalStateException("Could not close resource cleanly.", exception));
            }
        }

        if (failure != null) {
            throw failure;
        }
    }

    private static RuntimeException merge(RuntimeException failure, RuntimeException next) {
        if (failure == null) {
            return next;
        }
        if (next != failure) {
            failure.addSuppressed(next);
        }
        return failure;
    }
}
