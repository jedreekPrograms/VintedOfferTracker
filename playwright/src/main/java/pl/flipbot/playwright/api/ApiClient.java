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

    private static final String BASE_URL = "http://localhost:8081";

    protected final HttpClient httpClient;

    protected final ObjectMapper objectMapper;

    protected ApiClient() {
        this.httpClient = BackendHttpTransport.client();
        this.objectMapper = new ObjectMapper();
    }

    protected HttpResponse<String> get(String path) {
        HttpRequest request = BackendHttpTransport.request(
                        URI.create(BASE_URL + path)
                )
                .GET()
                .build();

        return send(request, "GET");
    }

    protected HttpResponse<String> post(String path, Object body) {
        String json = serialize(body, "POST");

        HttpRequest request = BackendHttpTransport.request(
                        URI.create(BASE_URL + path)
                )
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        return send(request, "POST");
    }

    protected HttpResponse<String> patch(String path, Object body) {
        String json = serialize(body, "PATCH");

        HttpRequest request = BackendHttpTransport.request(
                        URI.create(BASE_URL + path)
                )
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

    protected HttpResponse<String> post(String path) {
        HttpRequest request = BackendHttpTransport.request(
                        URI.create(BASE_URL + path)
                )
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        return send(request, "POST");
    }

    protected HttpResponse<String> patch(String path) {
        HttpRequest request = BackendHttpTransport.request(
                        URI.create(BASE_URL + path)
                )
                .method(
                        "PATCH",
                        HttpRequest.BodyPublishers.noBody()
                )
                .build();

        return send(request, "PATCH");
    }

    private String serialize(Object body, String operation) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (IOException exception) {
            throw new ApiException(
                    operation + " request body serialization failed.",
                    exception
            );
        }
    }

    private HttpResponse<String> send(
            HttpRequest request,
            String operation
    ) {
        try {
            return httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApiException(
                    operation + " request was interrupted.",
                    exception
            );
        } catch (IOException exception) {
            throw new ApiException(
                    operation + " request failed.",
                    exception
            );
        }
    }
}
