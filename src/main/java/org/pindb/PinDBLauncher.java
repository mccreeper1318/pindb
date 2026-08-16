package org.pindb;

import javafx.application.Application;

import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class PinDBLauncher {
    private static final long UPDATE_PARENT_WAIT_SECONDS = 10L;

    private PinDBLauncher() {
    }

    public static void main(String[] args) {
        waitForUpdaterParent(args);
        Application.launch(PinDBApplication.class, args);
    }

    static boolean isPostUpdateLaunch(String[] args) {
        return args != null && Arrays.stream(args)
                .anyMatch(argument -> argument != null && argument.startsWith("--updated-tag="));
    }

    private static void waitForUpdaterParent(String[] args) {
        if (!isPostUpdateLaunch(args)) {
            return;
        }

        ProcessHandle.current().parent().ifPresent(parent -> {
            try {
                parent.onExit().get(UPDATE_PARENT_WAIT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException | TimeoutException ignored) {
                // Do not strand a successfully installed update if the old process is slow to terminate.
            }
        });
    }
}
