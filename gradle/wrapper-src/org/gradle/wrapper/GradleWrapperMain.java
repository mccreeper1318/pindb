package org.gradle.wrapper;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class GradleWrapperMain {
    private GradleWrapperMain() {}

    public static void main(String[] args) throws Exception {
        Path projectDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path propertiesPath = projectDir.resolve("gradle/wrapper/gradle-wrapper.properties");
        if (!Files.isRegularFile(propertiesPath)) {
            throw new FileNotFoundException("Missing " + propertiesPath);
        }

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(propertiesPath)) {
            properties.load(input);
        }

        String distributionUrl = required(properties, "distributionUrl").replace("\\:", ":");
        String expectedSha256 = properties.getProperty("distributionSha256Sum", "").trim();
        String fileName = distributionUrl.substring(distributionUrl.lastIndexOf('/') + 1);
        String distributionName = fileName.replaceFirst("-bin\\.zip$", "").replaceFirst("-all\\.zip$", "");

        Path gradleUserHome = Paths.get(System.getenv().getOrDefault(
                "GRADLE_USER_HOME",
                Paths.get(System.getProperty("user.home"), ".gradle").toString()));
        Path installRoot = gradleUserHome.resolve("wrapper/dists").resolve(distributionName);
        Path gradleHome = installRoot.resolve(distributionName);
        Path executable = gradleHome.resolve("bin").resolve(isWindows() ? "gradle.bat" : "gradle");

        if (!Files.isRegularFile(executable)) {
            Files.createDirectories(installRoot);
            Path zip = installRoot.resolve(fileName);
            download(distributionUrl, zip);
            if (!expectedSha256.isBlank()) {
                String actual = sha256(zip);
                if (!actual.equalsIgnoreCase(expectedSha256)) {
                    Files.deleteIfExists(zip);
                    throw new SecurityException("Gradle distribution checksum mismatch. Expected "
                            + expectedSha256 + " but received " + actual);
                }
            }
            unzip(zip, installRoot);
            Files.deleteIfExists(zip);
            if (!isWindows()) {
                executable.toFile().setExecutable(true, false);
            }
        }

        List<String> command = new ArrayList<>();
        command.add(executable.toString());
        command.addAll(Arrays.asList(args));
        Process process = new ProcessBuilder(command)
                .directory(projectDir.toFile())
                .inheritIO()
                .start();
        System.exit(process.waitFor());
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing Gradle wrapper property: " + key);
        }
        return value.trim();
    }

    private static void download(String url, Path destination) throws Exception {
        System.out.println("Downloading " + url);
        Path temp = destination.resolveSibling(destination.getFileName() + ".part");
        Files.deleteIfExists(temp);
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "PinDB-Gradle-Wrapper")
                .GET()
                .build();
        HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(temp));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            Files.deleteIfExists(temp);
            throw new IOException("Failed to download Gradle: HTTP " + response.statusCode());
        }
        try {
            Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[1024 * 64];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    private static void unzip(Path zip, Path target) throws IOException {
        try (ZipInputStream input = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                Path output = target.resolve(entry.getName()).normalize();
                if (!output.startsWith(target)) {
                    throw new IOException("Unsafe ZIP entry: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(output);
                } else {
                    Files.createDirectories(output.getParent());
                    Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
