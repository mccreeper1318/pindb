package org.pindb.service;

import org.pindb.platform.OperatingSystem;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Opens web links away from the JavaFX application thread. */
public final class ExternalLinkService {
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "pindb-external-link");
        thread.setDaemon(true);
        return thread;
    });

    private ExternalLinkService() {
    }

    public static CompletableFuture<Boolean> openAsync(URI uri) {
        return openAsync(uri, ExternalLinkService::openBlocking, EXECUTOR);
    }

    static CompletableFuture<Boolean> openAsync(URI uri, LinkOpener opener, Executor executor) {
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(opener, "opener");
        Objects.requireNonNull(executor, "executor");
        return CompletableFuture.supplyAsync(() -> {
            try {
                opener.open(uri);
                return true;
            } catch (Exception exception) {
                return false;
            }
        }, executor);
    }

    static List<List<String>> commandsFor(String operatingSystem, URI uri) {
        String target = uri.toString();
        return switch (OperatingSystem.fromOsName(operatingSystem)) {
            case LINUX -> List.of(
                    List.of("xdg-open", target),
                    List.of("gio", "open", target)
            );
            case MACOS -> List.of(List.of("open", target));
            case WINDOWS -> List.of(List.of("rundll32", "url.dll,FileProtocolHandler", target));
            case OTHER -> List.of();
        };
    }

    private static void openBlocking(URI uri) throws Exception {
        List<Exception> failures = new ArrayList<>();
        for (List<String> command : commandsFor(System.getProperty("os.name"), uri)) {
            try {
                new ProcessBuilder(command).start();
                return;
            } catch (IOException exception) {
                failures.add(exception);
            }
        }

        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(uri);
                return;
            }
        } catch (RuntimeException | IOException exception) {
            failures.add(exception);
        }

        IOException failure = new IOException("No supported application could open the web link.");
        failures.forEach(failure::addSuppressed);
        throw failure;
    }

    @FunctionalInterface
    interface LinkOpener {
        void open(URI uri) throws Exception;
    }
}
