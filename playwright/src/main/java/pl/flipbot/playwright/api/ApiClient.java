package pl.flipbot.playwright.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import pl.flipbot.playwright.exception.ApiException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public abstract class ApiClient {

    private static final URI BASE_URI = BackendApiEndpoint.fromEnvironment();

    protected final HttpClient httpClient;

    protected final ObjectMapper objectMapper;

    protected ApiClient() {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    protected HttpResponse<String> get(String path) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(resolvePath(path))
                .GET()
                .build();

        return send(request, "GET");
    }

    protected HttpResponse<String> post(String path, Object body) {
        final String json;
        try {
            json = objectMapper.writeValueAsString(body);
        } catch (IOException exception) {
            throw new ApiException(
                    "Could not serialize POST request body.",
                    exception
            );
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(resolvePath(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        return send(request, "POST");
    }

    protected HttpResponse<String> patch(String path, Object body) {
        final String json;
        try {
            json = objectMapper.writeValueAsString(body);
        } catch (IOException exception) {
            throw new ApiException(
                    "Could not serialize PATCH request body.",
                    exception
            );
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(resolvePath(path))
                .header("Content-Type", "application/json")
                .method(
                        "PATCH",
                        HttpRequest.BodyPublishers.ofString(json)
                )
                .build();

        return send(request, "PATCH");
    }

    protected <T> T readBody(
            HttpResponse<String> response,
            Class<T> clazz
    ) {
        try {
            return objectMapper.readValue(
                    response.body(),
                    clazz
            );
        } catch (IOException exception) {
            throw new ApiException(
                    "Cannot parse response.",
                    exception
            );
        }
    }

    protected <T> T readBody(
            HttpResponse<String> response,
            TypeReference<T> typeReference
    ) {
        try {
            return objectMapper.readValue(
                    response.body(),
                    typeReference
            );
        } catch (IOException exception) {
            throw new ApiException(
                    "Cannot parse response.",
                    exception
            );
        }
    }

    protected HttpResponse<String> post(String path) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(resolvePath(path))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        return send(request, "POST");
    }

    protected HttpResponse<String> patch(String path) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(resolvePath(path))
                .method(
                        "PATCH",
                        HttpRequest.BodyPublishers.noBody()
                )
                .build();

        return send(request, "PATCH");
    }

    private URI resolvePath(String path) {
        if (path == null || path.isBlank() || !path.startsWith("/")) {
            throw new IllegalArgumentException(
                    "Backend API path must be an absolute path beginning with '/'"
            );
        }

        return BASE_URI.resolve(path);
    }

    private HttpResponse<String> send(
            HttpRequest request,
            String method
    ) {
        try {
            return httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApiException(
                    method + " request was interrupted.",
                    exception
            );
        } catch (IOException exception) {
            throw new ApiException(
                    method + " request failed.",
                    exception
            );
        }
    }
}
