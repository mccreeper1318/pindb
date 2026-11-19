package org.pindb.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalDocumentCopyServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void createsAndReleasesCopiesInsidePrivateSessionDirectory() throws Exception {
        Path root = temporaryDirectory.resolve("copies");
        try (ExternalDocumentCopyService service = new ExternalDocumentCopyService(root, 300L, ignored -> false)) {
            Path copy = service.createTemporaryCopy("Quarterly Report.PDF");
            Files.writeString(copy, "temporary document");

            assertTrue(copy.startsWith(service.sessionDirectory()));
            assertEquals(".pdf", suffix(copy));
            assertEquals(1, service.trackedCopyCount());

            service.release(copy);

            assertFalse(Files.exists(copy));
            assertEquals(0, service.trackedCopyCount());
        }
    }

    @Test
    void startupRemovesCrashedSessionsButPreservesOtherLiveProcesses() throws Exception {
        Path root = temporaryDirectory.resolve("copies");
        Path stale = Files.createDirectories(root.resolve("session-100-stale"));
        Files.writeString(stale.resolve("document-old.pdf"), "old");
        Path reusedPid = Files.createDirectories(root.resolve("session-300-old"));
        Files.writeString(reusedPid.resolve("document-reused.txt"), "old");
        Path active = Files.createDirectories(root.resolve("session-200-active"));
        Files.writeString(active.resolve("document-live.txt"), "live");

        try (ExternalDocumentCopyService service =
                     new ExternalDocumentCopyService(root, 300L, pid -> pid == 200L)) {
            assertFalse(Files.exists(stale));
            assertFalse(Files.exists(reusedPid));
            assertTrue(Files.exists(active));
            assertTrue(Files.isDirectory(service.sessionDirectory()));
        }

        assertTrue(Files.exists(active));
    }

    @Test
    void applicationCloseRemovesTrackedCopiesAndSessionDirectory() throws Exception {
        Path root = temporaryDirectory.resolve("copies");
        ExternalDocumentCopyService service = new ExternalDocumentCopyService(root, 400L, ignored -> false);
        Path session = service.sessionDirectory();
        Path first = service.createTemporaryCopy("one.txt");
        Path second = service.createTemporaryCopy("two.docx");
        Files.writeString(first, "one");
        Files.writeString(second, "two");

        service.close();

        assertFalse(Files.exists(first));
        assertFalse(Files.exists(second));
        assertFalse(Files.exists(session));
        assertThrows(IllegalStateException.class, () -> service.createTemporaryCopy("closed.txt"));
    }

    @Test
    void refusesToReleasePathsOutsideItsOwnSession() {
        Path root = temporaryDirectory.resolve("copies");
        try (ExternalDocumentCopyService service = new ExternalDocumentCopyService(root, 500L, ignored -> false)) {
            Path unrelated = temporaryDirectory.resolve("unrelated.txt");
            assertThrows(IllegalArgumentException.class, () -> service.release(unrelated));
        }
    }

    private static String suffix(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot);
    }
}
