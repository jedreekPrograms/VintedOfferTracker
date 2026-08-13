package pl.flipbot.playwright.worker;

import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

@Slf4j
public record ScheduledRealActionConfig(
        boolean realOffersRequested,
        boolean realNextStepsRequested,
        Set<Long> allowedBotIds,
        boolean confirmationValid,
        boolean preflightOnly,
        boolean productionModeRequested,
        boolean productionConfirmationValid
) {

    private static final String REAL_OFFERS_ENV =
            "FLIPBOT_REAL_OFFERS_ENABLED";

    private static final String REAL_NEXT_STEPS_ENV =
            "FLIPBOT_REAL_NEXT_STEPS_ENABLED";

    private static final String BOT_IDS_ENV =
            "FLIPBOT_REAL_ACTION_BOT_IDS";

    private static final String LEGACY_BOT_ID_ENV =
            "FLIPBOT_REAL_ACTION_BOT_ID";

    private static final String CONFIRM_ENV =
            "FLIPBOT_REAL_ACTION_CONFIRM";

    private static final String PREFLIGHT_ONLY_ENV =
            "FLIPBOT_REAL_ACTION_PREFLIGHT_ONLY";

    private static final String PRODUCTION_MODE_ENV =
            "FLIPBOT_REAL_ACTION_PRODUCTION_MODE";

    private static final String PRODUCTION_CONFIRM_ENV =
            "FLIPBOT_REAL_ACTION_PRODUCTION_CONFIRM";

    private static final String EXPECTED_CONFIRMATION =
            "I_UNDERSTAND_REAL_ACTIONS";

    private static final String EXPECTED_PRODUCTION_CONFIRMATION =
            "I_UNDERSTAND_CONTINUOUS_REAL_ACTIONS";

    public static ScheduledRealActionConfig fromEnvironment() {
        boolean realOffersRequested =
                readBoolean(REAL_OFFERS_ENV, false);

        boolean realNextStepsRequested =
                readBoolean(REAL_NEXT_STEPS_ENV, false);

        Set<Long> allowedBotIds =
                readAllowedBotIds();

        String confirmation =
                System.getenv(CONFIRM_ENV);

        boolean confirmationValid =
                EXPECTED_CONFIRMATION.equals(confirmation);

        boolean preflightOnly =
                readBoolean(PREFLIGHT_ONLY_ENV, false);

        boolean productionModeRequested =
                readBoolean(PRODUCTION_MODE_ENV, false);

        String productionConfirmation =
                System.getenv(PRODUCTION_CONFIRM_ENV);

        boolean productionConfirmationValid =
                EXPECTED_PRODUCTION_CONFIRMATION.equals(productionConfirmation);

        ScheduledRealActionConfig config =
                new ScheduledRealActionConfig(
                        realOffersRequested,
                        realNextStepsRequested,
                        allowedBotIds,
                        confirmationValid,
                        preflightOnly,
                        productionModeRequested,
                        productionConfirmationValid
                );

        if (!realOffersRequested && !realNextStepsRequested) {
            log.info(
                    "[REAL ACTION CONFIG] Scheduled real actions are disabled. "
                            + "All scheduler jobs remain DRY RUN."
            );

            return config;
        }

        if (allowedBotIds.isEmpty() || !confirmationValid) {
            log.error(
                    "[REAL ACTION CONFIG] Real actions were requested but the safety gate is incomplete. "
                            + "Required: at least one positive bot id in {} (or legacy {}) and exact {} token. "
                            + "Effective mode remains DRY RUN.",
                    BOT_IDS_ENV,
                    LEGACY_BOT_ID_ENV,
                    CONFIRM_ENV
            );

            return config;
        }

        if (productionModeRequested && !productionConfirmationValid) {
            log.error(
                    "[REAL ACTION CONFIG] Production mode was requested but its dedicated confirmation gate is incomplete. "
                            + "Exact {} token is required when {}=true. Effective mode remains DRY RUN.",
                    PRODUCTION_CONFIRM_ENV,
                    PRODUCTION_MODE_ENV
            );

            return config;
        }

        if (preflightOnly) {
            log.warn(
                    "[REAL ACTION CONFIG] PREFLIGHT ONLY mode is armed for bot allowlist {}. "
                            + "Requested first offers={}, next steps={}. "
                            + "No real submit can be executed while {}=true.",
                    allowedBotIds,
                    realOffersRequested,
                    realNextStepsRequested,
                    PREFLIGHT_ONLY_ENV
            );

            return config;
        }

        if (productionModeEnabled()) {
            log.warn(
                    "[REAL ACTION CONFIG] PRODUCTION REAL ACTION MODE is armed for bot allowlist {}. "
                            + "First offers requested={}, next steps requested={}. "
                            + "Persistent guards, backend quota and per-run action limits remain enforced. "
                            + "The process-wide first-offer one-shot test lock is disabled.",
                    allowedBotIds,
                    realOffersRequested,
                    realNextStepsRequested
            );

            return config;
        }

        log.warn(
                "[REAL ACTION CONFIG] CONTROLLED REAL ACTION MODE is armed for bot allowlist {}. "
                        + "First offers requested={}, next steps requested={}. "
                        + "Per-run and first-offer one-shot test limits remain enforced per bot.",
                allowedBotIds,
                realOffersRequested,
                realNextStepsRequested
        );

        return config;
    }

    public boolean realOffersRequestedFor(Long botId) {
        return realOffersRequested
                && isArmedFor(botId);
    }

    public boolean realNextStepsRequestedFor(Long botId) {
        return realNextStepsRequested
                && isArmedFor(botId);
    }

    public boolean realOffersEnabledFor(Long botId) {
        return realOffersRequestedFor(botId)
                && !preflightOnly;
    }

    public boolean realNextStepsEnabledFor(Long botId) {
        return realNextStepsRequestedFor(botId)
                && !preflightOnly;
    }

    public boolean productionModeEnabled() {
        return productionModeRequested
                && productionConfirmationValid
                && confirmationValid
                && !preflightOnly
                && !allowedBotIds.isEmpty();
    }

    public boolean firstOfferOneShotTestModeEnabled() {
        return !productionModeEnabled();
    }

    public boolean isArmedFor(Long botId) {
        return confirmationValid
                && (!productionModeRequested || productionConfirmationValid)
                && botId != null
                && allowedBotIds.contains(botId);
    }

    private static Set<Long> readAllowedBotIds() {
        String rawIds =
                System.getenv(BOT_IDS_ENV);

        if (rawIds != null && !rawIds.isBlank()) {
            Set<Long> parsedIds =
                    parsePositiveLongSetOrEmpty(
                            BOT_IDS_ENV,
                            rawIds
                    );

            if (System.getenv(LEGACY_BOT_ID_ENV) != null) {
                log.info(
                        "[REAL ACTION CONFIG] {} is set, so legacy {} is ignored.",
                        BOT_IDS_ENV,
                        LEGACY_BOT_ID_ENV
                );
            }

            return parsedIds;
        }

        Long legacyBotId =
                readPositiveLongOrNull(LEGACY_BOT_ID_ENV);

        if (legacyBotId == null) {
            return Set.of();
        }

        log.info(
                "[REAL ACTION CONFIG] Using legacy {}={}. Prefer {} for multi-bot allowlists.",
                LEGACY_BOT_ID_ENV,
                legacyBotId,
                BOT_IDS_ENV
        );

        return Set.of(legacyBotId);
    }

    private static Set<Long> parsePositiveLongSetOrEmpty(
            String environmentName,
            String rawValue
    ) {
        LinkedHashSet<Long> ids =
                new LinkedHashSet<>();

        String[] tokens =
                rawValue.split(",", -1);

        try {
            for (String token : tokens) {
                String trimmed =
                        token.trim();

                if (trimmed.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Bot id entry cannot be blank"
                    );
                }

                long parsed =
                        Long.parseLong(trimmed);

                if (parsed <= 0L) {
                    throw new IllegalArgumentException(
                            "Bot id must be positive"
                    );
                }

                ids.add(parsed);
            }
        } catch (RuntimeException exception) {
            log.warn(
                    "[REAL ACTION CONFIG] Invalid {} value '{}'. "
                            + "The entire real-action bot allowlist is disabled.",
                    environmentName,
                    rawValue
            );

            return Set.of();
        }

        if (ids.isEmpty()) {
            return Set.of();
        }

        return Collections.unmodifiableSet(ids);
    }

    private static boolean readBoolean(
            String environmentName,
            boolean defaultValue
    ) {
        String rawValue =
                System.getenv(environmentName);

        if (rawValue == null || rawValue.isBlank()) {
            return defaultValue;
        }

        String normalized =
                rawValue.trim().toLowerCase();

        return switch (normalized) {
            case "true", "1", "yes", "y", "on" -> true;
            case "false", "0", "no", "n", "off" -> false;
            default -> {
                log.warn(
                        "[REAL ACTION CONFIG] Invalid {} value. Using default {}.",
                        environmentName,
                        defaultValue
                );
                yield defaultValue;
            }
        };
    }

    private static Long readPositiveLongOrNull(
            String environmentName
    ) {
        String rawValue =
                System.getenv(environmentName);

        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }

        try {
            long parsed =
                    Long.parseLong(rawValue.trim());

            if (parsed <= 0L) {
                throw new IllegalArgumentException(
                        "Bot id must be positive"
                );
            }

            return parsed;

        } catch (RuntimeException exception) {
            log.warn(
                    "[REAL ACTION CONFIG] Invalid {} value. Real-action bot whitelist is disabled.",
                    environmentName
            );

            return null;
        }
    }
}
