package org.pindb.service;

import org.pindb.util.AppPaths;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongPredicate;

public final class ExternalDocumentCopyService implements AutoCloseable {
    private static final String SESSION_PREFIX = "session-";
    private static final Set<PosixFilePermission> DIRECTORY_PERMISSIONS = Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE);
    private static final Set<PosixFilePermission> FILE_PERMISSIONS = Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE);

    private final Path rootDirectory;
    private final Path sessionDirectory;
    private final Set<Path> trackedCopies = ConcurrentHashMap.newKeySet();
    private boolean closed;

    public ExternalDocumentCopyService() {
        this(AppPaths.cacheDirectory().resolve("ExternalDocumentCopies"),
                ProcessHandle.current().pid(),
                ExternalDocumentCopyService::isProcessAlive);
    }

    ExternalDocumentCopyService(Path rootDirectory, long currentPid, LongPredicate processAlive) {
        try {
            this.rootDirectory = preparePrivateDirectory(rootDirectory.toAbsolutePath().normalize());
            cleanupStaleSessions(this.rootDirectory, currentPid, processAlive);
            this.sessionDirectory = preparePrivateDirectory(this.rootDirectory.resolve(
                    SESSION_PREFIX + currentPid + "-" + UUID.randomUUID()));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not prepare PinDB's temporary document directory.", exception);
        }
    }

    public synchronized Path createTemporaryCopy(String originalFileName) throws IOException {
        ensureOpen();
        Path copy = Files.createTempFile(sessionDirectory, "document-", temporarySuffix(originalFileName))
                .toAbsolutePath().normalize();
        applyPrivatePermissions(copy, FILE_PERMISSIONS);
        trackedCopies.add(copy);
        return copy;
    }

    public boolean canOpenWithSystemApplication() {
        if (!Desktop.isDesktopSupported()) {
            return false;
        }
        try {
            return Desktop.getDesktop().isSupported(Desktop.Action.OPEN);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public void openWithSystemApplication(Path copy) throws IOException {
        Path managed = normalizeManagedCopy(copy);
        if (!trackedCopies.contains(managed)) {
            throw new IOException("The temporary document copy is no longer available.");
        }
        if (!canOpenWithSystemApplication()) {
            throw new IOException("This system does not provide a desktop file-opening service.");
        }
        Desktop.getDesktop().open(managed.toFile());
    }

    public synchronized void release(Path copy) {
        if (copy == null) {
            return;
        }
        Path managed = normalizeManagedCopy(copy);
        if (!trackedCopies.contains(managed)) {
            return;
        }
        try {
            Files.deleteIfExists(managed);
            trackedCopies.remove(managed);
        } catch (IOException exception) {
            managed.toFile().deleteOnExit();
        }
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        for (Path copy : new ArrayList<>(trackedCopies)) {
            releaseTrackedCopy(copy);
        }
        try {
            deleteRecursively(sessionDirectory);
        } catch (IOException ignored) {
            sessionDirectory.toFile().deleteOnExit();
        }
    }

    Path rootDirectory() {
        return rootDirectory;
    }

    Path sessionDirectory() {
        return sessionDirectory;
    }

    int trackedCopyCount() {
        return trackedCopies.size();
    }

    private void releaseTrackedCopy(Path copy) {
        try {
            Files.deleteIfExists(copy);
            trackedCopies.remove(copy);
        } catch (IOException exception) {
            copy.toFile().deleteOnExit();
        }
    }

    private Path normalizeManagedCopy(Path copy) {
        Path normalized = copy.toAbsolutePath().normalize();
        if (!normalized.startsWith(sessionDirectory)) {
            throw new IllegalArgumentException("Path is not managed by this PinDB temporary-copy session.");
        }
        return normalized;
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("The temporary document service is already closed.");
        }
    }

    private static Path preparePrivateDirectory(Path directory) throws IOException {
        Files.createDirectories(directory);
        applyPrivatePermissions(directory, DIRECTORY_PERMISSIONS);
        return directory;
    }

    private static void applyPrivatePermissions(Path path, Set<PosixFilePermission> permissions) throws IOException {
        if (Files.getFileAttributeView(path, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS) != null) {
            Files.setPosixFilePermissions(path, permissions);
        }
    }

    private static void cleanupStaleSessions(Path rootDirectory, long currentPid,
                                             LongPredicate processAlive) throws IOException {
        try (var entries = Files.list(rootDirectory)) {
            List<Path> sessions = entries.sorted(Comparator.comparing(Path::toString)).toList();
            for (Path entry : sessions) {
                Long ownerPid = sessionPid(entry.getFileName().toString());
                boolean activeOtherSession = ownerPid != null
                        && ownerPid != currentPid
                        && processAlive.test(ownerPid);
                if (!activeOtherSession) {
                    try {
                        deleteRecursively(entry);
                    } catch (IOException ignored) {
                        entry.toFile().deleteOnExit();
                    }
                }
            }
        }
    }

    private static Long sessionPid(String name) {
        if (name == null || !name.startsWith(SESSION_PREFIX)) {
            return null;
        }
        int pidStart = SESSION_PREFIX.length();
        int pidEnd = name.indexOf('-', pidStart);
        if (pidEnd <= pidStart) {
            return null;
        }
        try {
            return Long.parseLong(name.substring(pidStart, pidEnd));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static boolean isProcessAlive(long pid) {
        return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
    }

    private static String temporarySuffix(String originalFileName) {
        String safe = originalFileName == null ? "" : originalFileName.trim();
        int dot = safe.lastIndexOf('.');
        if (dot < 0 || dot == safe.length() - 1) {
            return ".tmp";
        }
        String extension = safe.substring(dot + 1)
                .replaceAll("[^A-Za-z0-9]", "")
                .toLowerCase(Locale.ROOT);
        if (extension.isBlank()) {
            return ".tmp";
        }
        if (extension.length() > 16) {
            extension = extension.substring(0, 16);
        }
        return "." + extension;
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (path == null || Files.notExists(path, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
                if (failure != null) {
                    throw failure;
                }
                Files.deleteIfExists(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
