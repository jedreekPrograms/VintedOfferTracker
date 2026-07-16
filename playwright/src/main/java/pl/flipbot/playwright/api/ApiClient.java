package pl.flipbot.playwright.api;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;

public class ApiClient {

    protected final HttpClient httpClient;

    protected final ObjectMapper objectMapper;

    protected final String baseUrl;

    public ApiClient() {

        this.httpClient = HttpClient.newHttpClient();

        this.objectMapper = new ObjectMapper();

        this.baseUrl = "http://localhost:8080";

    }
}
