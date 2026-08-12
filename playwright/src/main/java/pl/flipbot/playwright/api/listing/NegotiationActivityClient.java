package pl.flipbot.playwright.api.listing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.listing.dto.NegotiationActivityRequestDto;
import pl.flipbot.playwright.api.listing.dto.NegotiationActivityResponseDto;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.format.DateTimeFormatter;

@Slf4j
public class NegotiationActivityClient {

    private static final String BACKEND_BASE_URL =
            "http://localhost:8080";

    private static final DateTimeFormatter
            BACKEND_DATE_TIME_FORMAT =
            DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final HttpClient httpClient;

    private final ObjectMapper objectMapper;


    public NegotiationActivityClient() {

        this.httpClient =
                HttpClient.newHttpClient();

        this.objectMapper =
                new ObjectMapper();
    }


    public NegotiationActivityResponseDto recordActivity(
            Long botId,
            Long listingId,
            NegotiationActivityRequestDto request
    ) {

        if (botId == null) {

            throw new IllegalArgumentException(
                    "botId cannot be null"
            );
        }


        if (listingId == null) {

            throw new IllegalArgumentException(
                    "listingId cannot be null"
            );
        }


        if (request == null) {

            throw new IllegalArgumentException(
                    "request cannot be null"
            );
        }


        if (
                request.sellerActivityAt()
                        == null
                        && !request.readDetected()
        ) {

            return null;
        }


        String url =
                BACKEND_BASE_URL
                        + "/api/bots/"
                        + botId
                        + "/listings/"
                        + listingId
                        + "/negotiation-activity";


        String requestBody =
                createRequestBody(
                        request
                );


        HttpRequest httpRequest =
                HttpRequest.newBuilder()
                        .uri(
                                URI.create(
                                        url
                                )
                        )
                        .header(
                                "Content-Type",
                                "application/json"
                        )
                        .method(
                                "PATCH",
                                HttpRequest.BodyPublishers
                                        .ofString(
                                                requestBody
                                        )
                        )
                        .build();


        try {

            HttpResponse<String> response =
                    httpClient.send(
                            httpRequest,
                            HttpResponse.BodyHandlers
                                    .ofString()
                    );


            if (
                    response.statusCode() < 200
                            || response.statusCode() >= 300
            ) {

                throw new IllegalStateException(
                        "Backend rejected negotiation activity update. "
                                + "HTTP "
                                + response.statusCode()
                                + ". Body: "
                                + abbreviate(
                                response.body(),
                                500
                        )
                );
            }


            NegotiationActivityResponseDto responseDto =
                    parseResponse(
                            response.body()
                    );


            logBackendState(
                    listingId,
                    request,
                    responseDto
            );


            return responseDto;

        } catch (InterruptedException exception) {

            Thread.currentThread()
                    .interrupt();


            throw new IllegalStateException(
                    "Negotiation activity request was interrupted",
                    exception
            );

        } catch (IOException exception) {

            throw new IllegalStateException(
                    "Could not send negotiation activity to backend",
                    exception
            );
        }
    }


    private NegotiationActivityResponseDto parseResponse(
            String responseBody
    ) {

        if (
                responseBody == null
                        || responseBody.isBlank()
        ) {

            throw new IllegalStateException(
                    "Backend returned an empty negotiation activity response"
            );
        }


        try {

            return objectMapper.readValue(
                    responseBody,
                    NegotiationActivityResponseDto.class
            );

        } catch (JsonProcessingException exception) {

            throw new IllegalStateException(
                    "Could not parse negotiation activity response. Body: "
                            + abbreviate(
                            responseBody,
                            500
                    ),
                    exception
            );
        }
    }


    private void logBackendState(
            Long listingId,
            NegotiationActivityRequestDto request,
            NegotiationActivityResponseDto response
    ) {

        boolean sellerActivityRequested =
                request.sellerActivityAt()
                        != null;

        String requestedSellerActivityAt =
                sellerActivityRequested
                        ? BACKEND_DATE_TIME_FORMAT.format(
                        request.sellerActivityAt()
                )
                        : null;


        boolean sellerActivityStoredExactly =
                sellerActivityRequested
                        && requestedSellerActivityAt.equals(
                        response.sellerActivityAt()
                );


        /*
         * Nie piszemy już "Persisted activity" na podstawie samego HTTP 2xx.
         *
         * Backend może celowo odrzucić starą wiadomość z poprzedniego
         * kroku negocjacji i mimo to poprawnie zwrócić HTTP 200.
         * Logujemy więc faktyczny stan zwrócony przez backend.
         */
        if (
                sellerActivityRequested
                        && !sellerActivityStoredExactly
        ) {

            log.debug(
                    "[NEGOTIATION ACTIVITY API] Backend did not store "
                            + "requested seller activity for listing {}. "
                            + "Requested sellerActivityAt={}, "
                            + "stored sellerActivityAt={}, "
                            + "currentStep={}, currentStepStartedAt={}.",
                    listingId,
                    requestedSellerActivityAt,
                    response.sellerActivityAt(),
                    response.currentStep(),
                    response.currentStepStartedAt()
            );

        } else {

            log.info(
                    "[NEGOTIATION ACTIVITY API] Backend activity state "
                            + "for listing {}. sellerActivityAt={}, "
                            + "readDetectedAt={}, currentStep={}, "
                            + "currentStepStartedAt={}.",
                    listingId,
                    response.sellerActivityAt(),
                    response.readDetectedAt(),
                    response.currentStep(),
                    response.currentStepStartedAt()
            );
        }


        if (
                request.readDetected()
                        && response.readDetectedAt()
                        == null
        ) {

            log.warn(
                    "[NEGOTIATION ACTIVITY API] Read indicator was "
                            + "requested for backend listing {}, but backend "
                            + "returned readDetectedAt=null.",
                    listingId
            );
        }
    }


    private String createRequestBody(
            NegotiationActivityRequestDto request
    ) {

        String sellerActivityAtJson =
                request.sellerActivityAt()
                        == null
                        ? "null"
                        : "\""
                        + BACKEND_DATE_TIME_FORMAT
                        .format(
                                request.sellerActivityAt()
                        )
                        + "\"";


        return "{"
                + "\"sellerActivityAt\":"
                + sellerActivityAtJson
                + ","
                + "\"readDetected\":"
                + request.readDetected()
                + "}";
    }


    private String abbreviate(
            String value,
            int maximumLength
    ) {

        if (value == null) {

            return "<empty>";
        }


        String normalized =
                value.replaceAll(
                                "\\s+",
                                " "
                        )
                        .trim();


        if (
                normalized.length()
                        <= maximumLength
        ) {

            return normalized;
        }


        return normalized
                .substring(
                        0,
                        maximumLength
                )
                + "...";
    }
}