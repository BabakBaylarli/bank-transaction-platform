package com.bankplatform.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

/** Tiny JSON-over-HTTP client - the e2e test deliberately uses nothing from the services' code. */
final class Api {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();

    record Response(int status, JsonNode body, Map<String, java.util.List<String>> headers) {
    }

    Response get(String url, String token) throws IOException, InterruptedException {
        return send(HttpRequest.newBuilder(URI.create(url)).GET(), token, Map.of());
    }

    Response post(String url, String token, Object body, Map<String, String> headers)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        return send(builder, token, headers);
    }

    private Response send(HttpRequest.Builder builder, String token, Map<String, String> headers)
            throws IOException, InterruptedException {
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        headers.forEach(builder::header);
        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        JsonNode body = response.body() == null || response.body().isBlank()
                ? JSON.nullNode()
                : JSON.readTree(response.body());
        return new Response(response.statusCode(), body, response.headers().map());
    }
}
