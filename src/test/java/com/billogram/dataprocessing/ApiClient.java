package com.billogram.dataprocessing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

/** Minimal HTTP client for calling the running application in integration tests. */
public final class ApiClient {

    private final HttpClient client = HttpClient.newHttpClient();
    private final String baseUrl;

    public ApiClient(int port) {
        this.baseUrl = "http://localhost:" + port;
    }

    public HttpResponse<String> get(String pathAndQuery) {
        return send(request(pathAndQuery).GET().build());
    }

    public HttpResponse<String> post(String path, String json) {
        return send(jsonPost(path, json));
    }

    public CompletableFuture<HttpResponse<String>> postAsync(String path, String json) {
        return client.sendAsync(jsonPost(path, json), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest jsonPost(String path, String json) {
        return request(path)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path));
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calling " + request.uri(), e);
        }
    }
}
