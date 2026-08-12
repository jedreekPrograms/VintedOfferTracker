package pl.flipbot.playwright.negotiation;

import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.model.BotConfigurationDto;
import pl.flipbot.playwright.model.NegotiationStepDto;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
public class PendingNegotiationPolicy {

    private static final long ENGAGED_WAIT_HOURS =
            3L;

    private static final long NO_ENGAGEMENT_EXPIRY_HOURS =
            48L;


    public PendingNegotiationDecision decide(
            ListingResponseDto listing,
            ConversationActivitySnapshot activitySnapshot,
            BotConfigurationDto configuration
    ) {

        Objects.requireNonNull(
                listing,
                "Listing cannot be null"
        );

        Objects.requireNonNull(
                activitySnapshot,
                "Conversation activity snapshot cannot be null"
        );

        Objects.requireNonNull(
                configuration,
                "Bot configuration cannot be null"
        );


        LocalDateTime now =
                LocalDateTime.now();

        LocalDateTime currentStepStartedAt =
                parseDateTime(
                        listing.currentStepStartedAt(),
                        "currentStepStartedAt",
                        listing
                );


        LocalDateTime sellerActivityAt =
                resolveSellerActivityAt(
                        listing,
                        activitySnapshot,
                        currentStepStartedAt
                );


        /*
         * Odpowiedź sprzedającego jest mocniejszym sygnałem niż
         * samo "Przeczytane".
         *
         * Jeżeli mamy normalną wiadomość, liczymy 3h od OSTATNIEJ
         * wiadomości sprzedającego i nie przesuwamy zegara tylko dlatego,
         * że read-indicator został wykryty później przez workera.
         */
        if (sellerActivityAt != null) {

            LocalDateTime nextActionAt =
                    sellerActivityAt.plusHours(
                            ENGAGED_WAIT_HOURS
                    );


            if (now.isBefore(
                    nextActionAt
            )) {

                return PendingNegotiationDecision
                        .waitForSeller(
                                "The seller sent a normal message at "
                                        + sellerActivityAt
                                        + ". The bot will wait until "
                                        + nextActionAt
                                        + " before continuing the negotiation."
                        );
            }


            return continueOrExpire(
                    listing,
                    configuration,
                    "The seller sent a normal message at "
                            + sellerActivityAt
                            + " and at least "
                            + ENGAGED_WAIT_HOURS
                            + " hours have passed without a formal "
                            + "acceptance, rejection or counteroffer."
            );
        }


        LocalDateTime readDetectedAt =
                resolveReadDetectedAt(
                        listing,
                        activitySnapshot,
                        currentStepStartedAt,
                        now
                );


        if (readDetectedAt != null) {

            LocalDateTime nextActionAt =
                    readDetectedAt.plusHours(
                            ENGAGED_WAIT_HOURS
                    );


            if (now.isBefore(
                    nextActionAt
            )) {

                return PendingNegotiationDecision
                        .waitForSeller(
                                "The latest offer has been read. "
                                        + "The read timer started at "
                                        + readDetectedAt
                                        + ". The bot will wait until "
                                        + nextActionAt
                                        + " before continuing the negotiation."
                        );
            }


            return continueOrExpire(
                    listing,
                    configuration,
                    "The latest offer has been read and at least "
                            + ENGAGED_WAIT_HOURS
                            + " hours have passed without a formal "
                            + "acceptance, rejection or counteroffer."
            );
        }


        /*
         * Brak jakiejkolwiek aktywności.
         *
         * Nie wygaszamy negocjacji, jeżeli z jakiegoś powodu backend
         * nie zna momentu rozpoczęcia aktualnego kroku. W takiej sytuacji
         * bezpieczniej dalej czekać niż zakończyć rozmowę na podstawie
         * niepełnych danych.
         */
        if (currentStepStartedAt == null) {

            return PendingNegotiationDecision
                    .waitForSeller(
                            "The latest offer is pending and the backend "
                                    + "has no currentStepStartedAt timestamp, "
                                    + "so the 48-hour inactivity timer cannot "
                                    + "be evaluated safely."
                    );
        }


        LocalDateTime expiryAt =
                currentStepStartedAt.plusHours(
                        NO_ENGAGEMENT_EXPIRY_HOURS
                );


        if (now.isBefore(
                expiryAt
        )) {

            return PendingNegotiationDecision
                    .waitForSeller(
                            "The latest offer is pending with no seller "
                                    + "message and no read indicator. "
                                    + "The inactivity timeout is "
                                    + expiryAt
                                    + "."
                    );
        }


        return PendingNegotiationDecision
                .expire(
                        "The latest offer received no seller message, "
                                + "no read indicator and no formal response "
                                + "for at least "
                                + NO_ENGAGEMENT_EXPIRY_HOURS
                                + " hours."
                );
    }


    private PendingNegotiationDecision continueOrExpire(
            ListingResponseDto listing,
            BotConfigurationDto configuration,
            String activityReason
    ) {

        Optional<NegotiationStepDto> nextStep =
                findNextStep(
                        listing.currentStep(),
                        configuration.getNegotiationSteps()
                );


        if (nextStep.isPresent()) {

            return PendingNegotiationDecision
                    .sendNextStep(
                            nextStep.get(),
                            activityReason
                                    + " Another negotiation step is available."
                    );
        }


        return PendingNegotiationDecision
                .expire(
                        activityReason
                                + " There are no more automated "
                                + "negotiation steps, so the conversation "
                                + "will be closed as EXPIRED."
                );
    }


    private LocalDateTime resolveSellerActivityAt(
            ListingResponseDto listing,
            ConversationActivitySnapshot activitySnapshot,
            LocalDateTime currentStepStartedAt
    ) {

        LocalDateTime persistedSellerActivityAt =
                parseDateTime(
                        listing.sellerActivityAt(),
                        "sellerActivityAt",
                        listing
                );


        LocalDateTime detectedSellerActivityAt =
                activitySnapshot
                        .sellerMessageAfterLatestOwnOffer()
                        ? activitySnapshot
                        .latestSellerMessageAt()
                        : null;


        persistedSellerActivityAt =
                ignoreIfOlderThanCurrentStep(
                        persistedSellerActivityAt,
                        currentStepStartedAt,
                        "persisted seller activity",
                        listing
                );


        detectedSellerActivityAt =
                ignoreIfOlderThanCurrentStep(
                        detectedSellerActivityAt,
                        currentStepStartedAt,
                        "detected seller message",
                        listing
                );


        if (persistedSellerActivityAt == null) {

            return detectedSellerActivityAt;
        }


        if (detectedSellerActivityAt == null) {

            return persistedSellerActivityAt;
        }


        return detectedSellerActivityAt.isAfter(
                persistedSellerActivityAt
        )
                ? detectedSellerActivityAt
                : persistedSellerActivityAt;
    }


    private LocalDateTime resolveReadDetectedAt(
            ListingResponseDto listing,
            ConversationActivitySnapshot activitySnapshot,
            LocalDateTime currentStepStartedAt,
            LocalDateTime now
    ) {

        LocalDateTime persistedReadDetectedAt =
                parseDateTime(
                        listing.readDetectedAt(),
                        "readDetectedAt",
                        listing
                );


        persistedReadDetectedAt =
                ignoreIfOlderThanCurrentStep(
                        persistedReadDetectedAt,
                        currentStepStartedAt,
                        "persisted read detection",
                        listing
                );


        if (persistedReadDetectedAt != null) {

            return persistedReadDetectedAt;
        }


        /*
         * Przy pierwszym wykryciu "Przeczytane" backend zapisuje
         * readDetectedAt=now(). ListingResponseDto pobrano jednak przed
         * tym PATCH-em, więc podczas tego samego cyklu pole jest jeszcze
         * null.
         *
         * Używamy lokalnego now() tylko jako punktu startu dla TEGO cyklu.
         * W następnym cyklu źródłem prawdy będzie już timestamp z backendu.
         */
        if (
                activitySnapshot
                        .readIndicatorAfterLatestOwnOffer()
        ) {

            return now;
        }


        return null;
    }


    private LocalDateTime ignoreIfOlderThanCurrentStep(
            LocalDateTime activityAt,
            LocalDateTime currentStepStartedAt,
            String activityName,
            ListingResponseDto listing
    ) {

        if (
                activityAt == null
                        || currentStepStartedAt == null
        ) {

            return activityAt;
        }


        if (activityAt.isBefore(
                currentStepStartedAt
        )) {

            log.debug(
                    "[PENDING POLICY] Ignoring {} at {} for marketplace "
                            + "listing {} because current step {} started at {}.",
                    activityName,
                    activityAt,
                    listing.listingId(),
                    listing.currentStep(),
                    currentStepStartedAt
            );


            return null;
        }


        return activityAt;
    }


    private Optional<NegotiationStepDto> findNextStep(
            Integer currentStepNumber,
            List<NegotiationStepDto> negotiationSteps
    ) {

        if (currentStepNumber == null) {

            throw new IllegalStateException(
                    "Negotiating listing has no current step"
            );
        }


        if (
                negotiationSteps == null
                        || negotiationSteps.isEmpty()
        ) {

            throw new IllegalStateException(
                    "Bot configuration has no negotiation steps"
            );
        }


        return negotiationSteps.stream()
                .filter(
                        Objects::nonNull
                )
                .filter(
                        step -> step.getStepNumber()
                                != null
                )
                .filter(
                        step -> step.getStepNumber()
                                > currentStepNumber
                )
                .min(
                        Comparator.comparing(
                                NegotiationStepDto::getStepNumber
                        )
                )
                .map(
                        this::validateNextStep
                );
    }


    private NegotiationStepDto validateNextStep(
            NegotiationStepDto nextStep
    ) {

        if (nextStep.getOfferPrice() == null) {

            throw new IllegalStateException(
                    "Negotiation step "
                            + nextStep.getStepNumber()
                            + " has no offer price"
            );
        }


        if (
                nextStep.getOfferPrice()
                        .signum()
                        <= 0
        ) {

            throw new IllegalStateException(
                    "Negotiation step "
                            + nextStep.getStepNumber()
                            + " has an invalid offer price: "
                            + nextStep.getOfferPrice()
            );
        }


        return nextStep;
    }


    private LocalDateTime parseDateTime(
            String rawValue,
            String fieldName,
            ListingResponseDto listing
    ) {

        if (
                rawValue == null
                        || rawValue.isBlank()
        ) {

            return null;
        }


        try {

            return LocalDateTime.parse(
                    rawValue
            );

        } catch (DateTimeParseException exception) {

            log.warn(
                    "[PENDING POLICY] Could not parse {}={} for backend "
                            + "listing {}, marketplace listing {}. "
                            + "This timestamp will be ignored.",
                    fieldName,
                    rawValue,
                    listing.id(),
                    listing.listingId()
            );


            log.trace(
                    "[PENDING POLICY] Full timestamp parse exception.",
                    exception
            );


            return null;
        }
    }
}