package org.pindb;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PinDBApplicationTest {
    @Test
    void matchesEquivalentReleaseTags() {
        assertTrue(PinDBApplication.sameVersion("0.2.1-beta.4", "v0.2.1-beta.4"));
    }

    @Test
    void rejectsDifferentReleaseTags() {
        assertFalse(PinDBApplication.sameVersion("0.2.1-beta.3", "0.2.1-beta.4"));
    }

    @Test
    void selectsFinderStyleDatabaseLaunchArgument(@TempDir Path temporaryDirectory) throws IOException {
        Path database = Files.createFile(temporaryDirectory.resolve("Finder Open Test.pindb"));

        Path requested = PinDBApplication.requestedDatabase(List.of(
                "--updated-tag=0.3-beta.1",
                database.toString()
        ));

        assertEquals(database, requested);
    }

    @Test
    void ignoresOptionsAndMissingDatabaseArguments(@TempDir Path temporaryDirectory) {
        Path requested = PinDBApplication.requestedDatabase(List.of(
                "--update-failed=/tmp/update.log",
                temporaryDirectory.resolve("missing.pindb").toString()
        ));

        assertNull(requested);
    }
}
