package pl.flipbot.playwright.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import pl.flipbot.playwright.exception.ApiException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public abstract class ApiClient {

    private static final String BASE_URL = "http://localhost:8081";

    private static final Duration CONNECT_TIMEOUT =
            Duration.ofSeconds(5);

    private static final Duration REQUEST_TIMEOUT =
            Duration.ofSeconds(20);

    private static final HttpClient SHARED_HTTP_CLIENT =
            HttpClient.newBuilder()
                    .connectTimeout(CONNECT_TIMEOUT)
                    .build();

    private static final ObjectMapper SHARED_OBJECT_MAPPER =
            new ObjectMapper();

    protected final HttpClient httpClient;

    protected final ObjectMapper objectMapper;

    protected ApiClient() {
        this.httpClient = SHARED_HTTP_CLIENT;
        this.objectMapper = SHARED_OBJECT_MAPPER;
    }

    protected HttpResponse<String> get(String path) {
        HttpRequest request = requestBuilder(path)
                .GET()
                .build();

        return send(request, "GET");
    }

    protected HttpResponse<String> post(String path, Object body) {
        try {
            String json = objectMapper.writeValueAsString(body);

            HttpRequest request = requestBuilder(path)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            return send(request, "POST");
        } catch (IOException exception) {
            throw new ApiException(
                    "POST request failed.",
                    exception
            );
        }
    }

    protected HttpResponse<String> patch(String path, Object body) {
        try {
            String json = objectMapper.writeValueAsString(body);

            HttpRequest request = requestBuilder(path)
                    .header("Content-Type", "application/json")
                    .method(
                            "PATCH",
                            HttpRequest.BodyPublishers.ofString(json)
                    )
                    .build();

            return send(request, "PATCH");
        } catch (IOException exception) {
            throw new ApiException(
                    "PATCH request failed.",
                    exception
            );
        }
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
        HttpRequest request = requestBuilder(path)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        return send(request, "POST");
    }

    protected HttpResponse<String> patch(String path) {
        HttpRequest request = requestBuilder(path)
                .method(
                        "PATCH",
                        HttpRequest.BodyPublishers.noBody()
                )
                .build();

        return send(request, "PATCH");
    }

    protected HttpRequest.Builder requestBuilder(String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .timeout(REQUEST_TIMEOUT);
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
                    method + " request failed.",
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
