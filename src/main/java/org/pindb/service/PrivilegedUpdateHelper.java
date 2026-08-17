package org.pindb.service;

import org.pindb.platform.LinuxPackageType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Narrow, package-installed entry point for privileged native-package updates.
 *
 * <p>This class is launched only by the root-owned {@code pindb-update-helper}
 * native launcher installed with PinDB. It never installs directly from a path writable
 * by the desktop user: the source is opened without following its final symlink,
 * copied through that open handle into a root-owned temporary directory, and
 * verified there before the package manager receives the staged path.</p>
 */
public final class PrivilegedUpdateHelper {
    private static final Path STAGING_ROOT = Path.of("/var/tmp");
    private static final Path INSTALL_DIRECTORY = Path.of("/opt/pindb");
    private static final Path BACKUP_ROOT = Path.of("/opt");
    private static final FileAttribute<?> OWNER_ONLY_DIRECTORY =
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"));
    private static final FileAttribute<?> OWNER_ONLY_FILE =
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"));

    private PrivilegedUpdateHelper() {
    }

    public static void main(String[] arguments) {
        try {
            requireRoot();
            ParsedArguments parsed = parseArguments(arguments);
            installVerifiedPackage(parsed.packageFile(), parsed.digest(), parsed.type(), STAGING_ROOT,
                    PrivilegedUpdateHelper::installStagedPackage, () -> { });
        } catch (Exception exception) {
            String message = exception.getMessage();
            System.err.println(message == null || message.isBlank()
                    ? "The privileged PinDB update helper failed." : message);
            System.exit(1);
        }
    }

    private static ParsedArguments parseArguments(String[] arguments) throws IOException {
        if (arguments.length != 4 || !"install".equals(arguments[0])) {
            throw new IOException("Usage: pindb-update-helper install <deb|rpm> <sha256> <absolute-package-path>");
        }
        LinuxPackageType type = switch (arguments[1]) {
            case "deb" -> LinuxPackageType.DEB;
            case "rpm" -> LinuxPackageType.RPM;
            default -> throw new IOException("Unsupported PinDB package type: " + arguments[1]);
        };
        String digest = normalizeDigest(arguments[2]);
        Path packageFile;
        try {
            packageFile = Path.of(arguments[3]);
        } catch (RuntimeException exception) {
            throw new IOException("The update package path is invalid.", exception);
        }
        if (!packageFile.isAbsolute() || packageFile.getFileName() == null
                || !type.matchesFileName(packageFile.getFileName().toString())) {
            throw new IOException("The update package must be an absolute path ending in " + type.extension() + ".");
        }
        return new ParsedArguments(packageFile.normalize(), digest, type);
    }

    private static void requireRoot() throws IOException {
        try {
            Object uid = Files.getAttribute(Path.of("/proc/self"), "unix:uid", LinkOption.NOFOLLOW_LINKS);
            if (!(uid instanceof Number number) || number.longValue() != 0L) {
                throw new IOException("The PinDB update helper must run as root through pkexec.");
            }
        } catch (UnsupportedOperationException exception) {
            throw new IOException("The PinDB update helper requires a Linux filesystem.", exception);
        }
    }

    static void installVerifiedPackage(Path packageFile, String expectedDigest, LinuxPackageType type,
                                       Path stagingRoot, PackageInstaller installer,
                                       IoAction afterSourceOpen) throws Exception {
        try (StagedPackage staged = stagePackage(packageFile, expectedDigest, type, stagingRoot,
                afterSourceOpen)) {
            installer.install(staged.packageFile(), type);
        }
    }

    static StagedPackage stagePackage(Path packageFile, String expectedDigest, LinuxPackageType type,
                                      Path stagingRoot, IoAction afterSourceOpen) throws Exception {
        String digest = normalizeDigest(expectedDigest);
        Path absolutePackage = packageFile.toAbsolutePath().normalize();
        Path parent = absolutePackage.getParent();
        Path fileName = absolutePackage.getFileName();
        if (parent == null || fileName == null || !type.matchesFileName(fileName.toString())) {
            throw new IOException("The update package path is invalid for a " + type.extension() + " package.");
        }

        Path stagingDirectory = null;
        try (DirectoryStream<Path> directory = Files.newDirectoryStream(parent)) {
            if (!(directory instanceof SecureDirectoryStream<Path> secureDirectory)) {
                throw new IOException("The update package filesystem does not support secure file opening.");
            }
            BasicFileAttributeView attributeView = secureDirectory.getFileAttributeView(fileName,
                    BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
            if (attributeView == null) {
                throw new IOException("The update package attributes could not be read securely.");
            }
            BasicFileAttributes attributes = attributeView.readAttributes();
            if (!attributes.isRegularFile()) {
                throw new IOException("The update package must be a regular file and cannot be a symbolic link.");
            }

            Set<OpenOption> options = Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
            try (SeekableByteChannel input = secureDirectory.newByteChannel(fileName, options)) {
                afterSourceOpen.run();
                stagingDirectory = Files.createTempDirectory(stagingRoot, "pindb-update-", OWNER_ONLY_DIRECTORY);
                Path stagedFile = stagingDirectory.resolve("package" + type.extension());
                String actualDigest = copyAndDigest(input, stagedFile);
                if (!actualDigest.equals(digest)) {
                    throw new IOException("The staged update failed its SHA-256 verification.");
                }
                return new StagedPackage(stagingDirectory, stagedFile);
            }
        } catch (Exception exception) {
            deleteTree(stagingDirectory);
            throw exception;
        }
    }

    private static String copyAndDigest(SeekableByteChannel input, Path destination) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var output = Files.newByteChannel(destination,
                Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS),
                OWNER_ONLY_FILE)) {
            ByteBuffer buffer = ByteBuffer.allocate(64 * 1024);
            while (input.read(buffer) >= 0) {
                buffer.flip();
                if (buffer.hasRemaining()) {
                    ByteBuffer digestBytes = buffer.asReadOnlyBuffer();
                    digest.update(digestBytes);
                    while (buffer.hasRemaining()) {
                        output.write(buffer);
                    }
                }
                buffer.clear();
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String normalizeDigest(String value) throws IOException {
        String digest = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!digest.matches("[0-9a-f]{64}")) {
            throw new IOException("The expected update SHA-256 digest is invalid.");
        }
        return digest;
    }

    static List<Path> packageManagerCandidates(LinuxPackageType type) {
        return type == LinuxPackageType.DEB
                ? List.of(Path.of("/usr/bin/apt-get"))
                : List.of(Path.of("/usr/bin/dnf5"), Path.of("/usr/bin/dnf"));
    }

    private static void installStagedPackage(Path stagedPackage, LinuxPackageType type) throws Exception {
        Path manager = packageManagerCandidates(type).stream().filter(Files::isExecutable).findFirst()
                .orElseThrow(() -> new IOException(type == LinuxPackageType.DEB
                        ? "The apt-get package installer is unavailable."
                        : "Neither dnf5 nor dnf is available under /usr/bin."));
        Path backupDirectory = null;
        Path backupCopy = null;
        if (Files.isDirectory(INSTALL_DIRECTORY, LinkOption.NOFOLLOW_LINKS)) {
            backupDirectory = Files.createTempDirectory(BACKUP_ROOT, ".pindb-update-backup-",
                    OWNER_ONLY_DIRECTORY);
            backupCopy = backupDirectory.resolve("pindb");
            ProcessResult backup = runCommand(List.of("/bin/cp", "-a", "--",
                    INSTALL_DIRECTORY.toString(), backupCopy.toString()), false);
            if (backup.status() != 0) {
                deleteTree(backupDirectory);
                throw new IOException("Could not back up the current PinDB installation.\n" + backup.output());
            }
        }

        try {
            List<String> command = List.of(manager.toString(), "install", "-y", stagedPackage.toString());
            ProcessResult installation = runCommand(command, type == LinuxPackageType.DEB);
            if (installation.status() == 0) {
                if (!installation.output().isBlank()) {
                    System.out.println(installation.output());
                }
                return;
            }
            if (backupCopy != null) {
                deleteTree(INSTALL_DIRECTORY);
                Files.move(backupCopy, INSTALL_DIRECTORY, StandardCopyOption.ATOMIC_MOVE);
            }
            throw new IOException("The package manager exited with status " + installation.status() + ".\n"
                    + (installation.output().isBlank()
                    ? "No additional installer output was provided." : installation.output()));
        } finally {
            deleteTree(backupDirectory);
        }
    }

    private static ProcessResult runCommand(List<String> command, boolean nonInteractiveDebian) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        if (nonInteractiveDebian) {
            builder.environment().put("DEBIAN_FRONTEND", "noninteractive");
        }
        Process process = builder.start();
        String output;
        try (InputStream input = process.getInputStream()) {
            output = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
        }
        return new ProcessResult(process.waitFor(), output);
    }

    private static void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
                if (failure != null) {
                    throw failure;
                }
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    @FunctionalInterface
    interface PackageInstaller {
        void install(Path stagedPackage, LinuxPackageType type) throws Exception;
    }

    @FunctionalInterface
    interface IoAction {
        void run() throws Exception;
    }

    record StagedPackage(Path directory, Path packageFile) implements AutoCloseable {
        @Override
        public void close() throws IOException {
            deleteTree(directory);
        }
    }

    private record ParsedArguments(Path packageFile, String digest, LinuxPackageType type) {
    }

    private record ProcessResult(int status, String output) {
    }
}
