package org.pindb.service;

import org.pindb.AppVersion;
import org.pindb.util.MiniJson;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public final class GitHubAuthService {
    private static final URI DEVICE_CODE_ENDPOINT = URI.create("https://github.com/login/device/code");
    private static final URI TOKEN_ENDPOINT = URI.create("https://github.com/login/oauth/access_token");
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private final GitHubCredentialStore store = new GitHubCredentialStore();

    public String accessToken(Consumer<DeviceAuthorization> authorizationCallback)
            throws IOException, InterruptedException {
        if (!GitHubAppConfig.configured()) {
            throw new IOException("The PinDB GitHub App client ID has not been configured.");
        }
        Token stored = store.load().orElse(null);
        if (stored != null && stored.accessValid()) {
            return stored.accessToken();
        }
        if (stored != null && stored.refreshValid()) {
            try {
                Token refreshed = refresh(stored.refreshToken());
                store.save(refreshed);
                return refreshed.accessToken();
            } catch (IOException exception) {
                store.clear();
            }
        }
        DeviceAuthorization authorization = requestDeviceAuthorization();
        authorizationCallback.accept(authorization);
        Token authorized = pollForToken(authorization);
        store.save(authorized);
        return authorized.accessToken();
    }

    public void disconnect() {
        store.clear();
    }

    private DeviceAuthorization requestDeviceAuthorization() throws IOException, InterruptedException {
        String body = form(Map.of("client_id", GitHubAppConfig.clientId()));
        HttpResponse<String> response = sendForm(DEVICE_CODE_ENDPOINT, body);
        ensureSuccess(response, "requesting GitHub authorization");
        Map<String, Object> json = MiniJson.object(MiniJson.parse(response.body()));
        return new DeviceAuthorization(
                MiniJson.string(json.get("device_code")),
                MiniJson.string(json.get("user_code")),
                URI.create(MiniJson.string(json.get("verification_uri"))),
                intValue(json.get("expires_in"), 900),
                intValue(json.get("interval"), 5));
    }

    private Token pollForToken(DeviceAuthorization authorization) throws IOException, InterruptedException {
        Instant deadline = Instant.now().plusSeconds(authorization.expiresInSeconds());
        int interval = Math.max(5, authorization.intervalSeconds());
        while (Instant.now().isBefore(deadline)) {
            Thread.sleep(interval * 1000L);
            String body = form(Map.of(
                    "client_id", GitHubAppConfig.clientId(),
                    "device_code", authorization.deviceCode(),
                    "grant_type", "urn:ietf:params:oauth:grant-type:device_code"));
            HttpResponse<String> response = sendForm(TOKEN_ENDPOINT, body);
            Map<String, Object> json = MiniJson.object(MiniJson.parse(response.body()));
            String error = MiniJson.string(json.get("error"));
            if (error.isBlank()) {
                return tokenFrom(json);
            }
            if ("authorization_pending".equals(error)) {
                continue;
            }
            if ("slow_down".equals(error)) {
                interval += 5;
                continue;
            }
            throw new IOException("GitHub authorization failed: "
                    + Objects.requireNonNullElse(MiniJson.string(json.get("error_description")), error));
        }
        throw new IOException("GitHub authorization expired before it was completed.");
    }

    private Token refresh(String refreshToken) throws IOException, InterruptedException {
        HttpResponse<String> response = sendForm(TOKEN_ENDPOINT, form(Map.of(
                "client_id", GitHubAppConfig.clientId(),
                "grant_type", "refresh_token",
                "refresh_token", refreshToken)));
        ensureSuccess(response, "refreshing GitHub authorization");
        Map<String, Object> json = MiniJson.object(MiniJson.parse(response.body()));
        if (!MiniJson.string(json.get("error")).isBlank()) {
            throw new IOException("GitHub authorization could not be refreshed.");
        }
        return tokenFrom(json);
    }

    private Token tokenFrom(Map<String, Object> json) throws IOException {
        String access = MiniJson.string(json.get("access_token"));
        if (access.isBlank()) {
            throw new IOException("GitHub did not return an access token.");
        }
        Instant now = Instant.now();
        return new Token(access, MiniJson.string(json.get("refresh_token")),
                now.plusSeconds(intValue(json.get("expires_in"), 28800)),
                now.plusSeconds(intValue(json.get("refresh_token_expires_in"), 15897600)));
    }

    private HttpResponse<String> sendForm(URI uri, String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(40))
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("User-Agent", "PinDB/" + AppVersion.VERSION)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static void ensureSuccess(HttpResponse<?> response, String action) throws IOException {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("GitHub returned HTTP " + response.statusCode() + " while " + action + ".");
        }
    }

    private static String form(Map<String, String> values) {
        return values.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right).orElse("");
    }

    private static String encode(String value) {
        return URLEncoder.encode(Objects.requireNonNullElse(value, ""), StandardCharsets.UTF_8);
    }

    private static int intValue(Object value, int fallback) {
        try {
            return Integer.parseInt(MiniJson.string(value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    public record DeviceAuthorization(String deviceCode, String userCode, URI verificationUri,
                                      int expiresInSeconds, int intervalSeconds) {
    }

    public record Token(String accessToken, String refreshToken, Instant expiresAt, Instant refreshExpiresAt) {
        public Token {
            accessToken = Objects.requireNonNullElse(accessToken, "");
            refreshToken = Objects.requireNonNullElse(refreshToken, "");
            expiresAt = Objects.requireNonNullElse(expiresAt, Instant.EPOCH);
            refreshExpiresAt = Objects.requireNonNullElse(refreshExpiresAt, Instant.EPOCH);
        }

        boolean accessValid() {
            return !accessToken.isBlank() && Instant.now().plusSeconds(60).isBefore(expiresAt);
        }

        boolean refreshValid() {
            return !refreshToken.isBlank() && Instant.now().plusSeconds(60).isBefore(refreshExpiresAt);
        }
    }
}
