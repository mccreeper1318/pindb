package org.pindb.service;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalLinkServiceTest {
    @Test
    void linuxPrefersDesktopOpenCommands() {
        URI uri = URI.create("https://github.com/login/device");

        assertEquals(List.of(
                List.of("xdg-open", uri.toString()),
                List.of("gio", "open", uri.toString())
        ), ExternalLinkService.commandsFor("Linux", uri));
    }

    @Test
    void openingRunsOnProvidedExecutorInsteadOfCaller() {
        AtomicBoolean executorUsed = new AtomicBoolean(false);
        AtomicBoolean openerCalled = new AtomicBoolean(false);
        Executor executor = command -> {
            executorUsed.set(true);
            Thread thread = new Thread(command, "external-link-test");
            thread.start();
            try {
                thread.join();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        };

        boolean opened = ExternalLinkService.openAsync(
                URI.create("https://example.com"),
                uri -> openerCalled.set(true),
                executor).join();

        assertTrue(opened);
        assertTrue(executorUsed.get());
        assertTrue(openerCalled.get());
    }

    @Test
    void failedOpenCompletesWithoutThrowing() {
        boolean opened = ExternalLinkService.openAsync(
                URI.create("https://example.com"),
                uri -> { throw new IllegalStateException("No browser"); },
                Runnable::run).join();

        assertFalse(opened);
    }
}
