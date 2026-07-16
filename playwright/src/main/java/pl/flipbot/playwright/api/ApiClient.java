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

    private static final String BASE_URL = "http://localhost:8080";

    protected final HttpClient httpClient;

    protected final ObjectMapper objectMapper;

    protected ApiClient() {

        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();

    }

    protected HttpResponse<String> get(String path) {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .GET()
                .build();

        try {

            return httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

        } catch (IOException | InterruptedException e) {

            throw new ApiException(
                    "GET request failed.",
                    e
            );

        }

    }

    protected HttpResponse<String> post(String path, Object body) {

        try {

            String json = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + path))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            return httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

        } catch (IOException | InterruptedException e) {

            throw new ApiException(
                    "POST request failed.",
                    e
            );

        }

    }

    protected HttpResponse<String> patch(String path, Object body) {

        try {

            String json = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + path))
                    .header("Content-Type", "application/json")
                    .method(
                            "PATCH",
                            HttpRequest.BodyPublishers.ofString(json)
                    )
                    .build();

            return httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

        } catch (IOException | InterruptedException e) {

            throw new ApiException(
                    "PATCH request failed.",
                    e
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

        } catch (IOException e) {

            throw new ApiException(
                    "Cannot parse response.",
                    e
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

        } catch (IOException e) {

            throw new ApiException(
                    "Cannot parse response.",
                    e
            );

        }

    }

}