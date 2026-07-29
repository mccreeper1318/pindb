package org.pindb.service;

import org.pindb.AppVersion;
import org.pindb.util.MiniJson;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public final class BugReportService {
    private static final URI ISSUES_API = URI.create(
            "https://api.github.com/repos/" + UpdateService.REPOSITORY + "/issues");
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    public SubmittedIssue submit(String token, BugReport report) throws IOException, InterruptedException {
        String body = report.markdownBody();
        String json = MiniJson.stringify(Map.of(
                "title", report.title(),
                "body", body,
                "labels", List.of("bug")
        ));
        HttpRequest request = HttpRequest.newBuilder(ISSUES_API)
                .timeout(Duration.ofSeconds(40))
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer " + token)
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "PinDB/" + AppVersion.VERSION)
                .POST(HttpRequest.BodyPublishers.ofString(json)).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 201) {
            throw new IOException("GitHub returned HTTP " + response.statusCode()
                    + " while creating the issue. Ensure the PinDB GitHub App is installed on the repository "
                    + "with Issues: Read and write permission.");
        }
        Map<String, Object> created = MiniJson.object(MiniJson.parse(response.body()));
        return new SubmittedIssue(Long.parseLong(MiniJson.string(created.get("number"))),
                URI.create(MiniJson.string(created.get("html_url"))));
    }

    public record BugReport(String title, String description, String steps, String expected,
                            String actual, String additional, boolean includeDiagnostics) {
        public String markdownBody() {
            StringBuilder markdown = new StringBuilder()
                    .append("## Description\n\n").append(clean(description)).append("\n\n")
                    .append("## Steps to reproduce\n\n").append(clean(steps)).append("\n\n")
                    .append("## Expected behavior\n\n").append(clean(expected)).append("\n\n")
                    .append("## Actual behavior\n\n").append(clean(actual)).append("\n\n");
            if (additional != null && !additional.isBlank()) {
                markdown.append("## Additional information\n\n").append(clean(additional)).append("\n\n");
            }
            if (includeDiagnostics) {
                markdown.append("## PinDB diagnostics\n\n```text\n")
                        .append("PinDB version: ").append(AppVersion.VERSION).append('\n')
                        .append("Operating system: ").append(System.getProperty("os.name", "Unknown"))
                        .append(' ').append(System.getProperty("os.version", "")).append('\n')
                        .append("Architecture: ").append(System.getProperty("os.arch", "Unknown")).append('\n')
                        .append("Java version: ").append(System.getProperty("java.version", "Unknown")).append('\n')
                        .append("JavaFX version: ").append(System.getProperty("javafx.version", "Unknown")).append('\n')
                        .append("```\n");
            }
            markdown.append("\n---\nSubmitted from PinDB's in-app bug reporter.");
            return markdown.toString();
        }

        private static String clean(String value) {
            return value == null || value.isBlank() ? "Not provided." : value.trim();
        }
    }

    public record SubmittedIssue(long number, URI url) {
    }
}
