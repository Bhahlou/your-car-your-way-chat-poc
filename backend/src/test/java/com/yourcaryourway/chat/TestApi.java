package com.yourcaryourway.chat;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

/** Small HTTP test client: calls the real API and returns the status and the JSON of the response. */
final class TestApi {

    static final String PASSWORD = "Password123!";

    record Response(int status, JsonNode json) {
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final HttpClient http = HttpClient.newHttpClient();
    private final String baseUrl;

    TestApi(int port) {
        this.baseUrl = "http://localhost:" + port;
    }

    /** Logs in with a demo account and returns the JWT. */
    String login(String email) {
        Response response = send("POST", "/api/v1/auth/login", null, Map.of("email", email, "password", PASSWORD));
        if (response.status() != 200) {
            throw new IllegalStateException("Connexion impossible pour " + email + " : " + response.status());
        }
        return response.json().get("accessToken").asString();
    }

    Response get(String path, String token) {
        return send("GET", path, token, null);
    }

    Response post(String path, String token, Object body) {
        return send("POST", path, token, body);
    }

    private Response send(String method, String path, String token, Object body) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json");
            if (token != null) {
                request.header("Authorization", "Bearer " + token);
            }
            request.method(method, body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode json = response.body().isBlank() ? null : JSON.readTree(response.body());
            return new Response(response.statusCode(), json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
