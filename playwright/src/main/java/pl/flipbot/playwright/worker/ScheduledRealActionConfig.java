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
        boolean productionConfirmationValid,
        boolean allowAllRunningBotsRequested
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

    private static final String ALLOW_ALL_RUNNING_BOTS_ENV =
            "FLIPBOT_REAL_ACTION_ALLOW_ALL_RUNNING_BOTS";

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

        boolean confirmationValid =
                EXPECTED_CONFIRMATION.equals(
                        System.getenv(CONFIRM_ENV)
                );

        boolean preflightOnly =
                readBoolean(PREFLIGHT_ONLY_ENV, false);

        boolean productionModeRequested =
                readBoolean(PRODUCTION_MODE_ENV, false);

        boolean productionConfirmationValid =
                EXPECTED_PRODUCTION_CONFIRMATION.equals(
                        System.getenv(PRODUCTION_CONFIRM_ENV)
                );

        boolean allowAllRunningBotsRequested =
                readBoolean(ALLOW_ALL_RUNNING_BOTS_ENV, false);

        ScheduledRealActionConfig config =
                new ScheduledRealActionConfig(
                        realOffersRequested,
                        realNextStepsRequested,
                        allowedBotIds,
                        confirmationValid,
                        preflightOnly,
                        productionModeRequested,
                        productionConfirmationValid,
                        allowAllRunningBotsRequested
                );

        if (!realOffersRequested && !realNextStepsRequested) {
            log.info(
                    "[REAL ACTION CONFIG] Scheduled real actions are disabled. "
                            + "All scheduler jobs remain DRY RUN."
            );
            return config;
        }

        if (!confirmationValid) {
            log.error(
                    "[REAL ACTION CONFIG] Real actions were requested but {} is missing or invalid. "
                            + "Effective mode remains DRY RUN.",
                    CONFIRM_ENV
            );
            return config;
        }

        if (productionModeRequested && !productionConfirmationValid) {
            log.error(
                    "[REAL ACTION CONFIG] Production mode was requested but {} is missing or invalid. "
                            + "Effective mode remains DRY RUN.",
                    PRODUCTION_CONFIRM_ENV
            );
            return config;
        }

        if (allowAllRunningBotsRequested && !productionModeRequested) {
            log.error(
                    "[REAL ACTION CONFIG] {}=true is allowed only together with {}=true. "
                            + "Effective mode remains DRY RUN.",
                    ALLOW_ALL_RUNNING_BOTS_ENV,
                    PRODUCTION_MODE_ENV
            );
            return config;
        }

        if (!config.hasConfiguredActionScope()) {
            log.error(
                    "[REAL ACTION CONFIG] Real actions were requested but no valid bot scope is configured. "
                            + "Use at least one positive bot id in {} (or legacy {}) or, in production mode only, {}=true. "
                            + "Effective mode remains DRY RUN.",
                    BOT_IDS_ENV,
                    LEGACY_BOT_ID_ENV,
                    ALLOW_ALL_RUNNING_BOTS_ENV
            );
            return config;
        }

        if (allowAllRunningBotsRequested && !allowedBotIds.isEmpty()) {
            log.info(
                    "[REAL ACTION CONFIG] {}=true is active, so explicit bot allowlist {} is not restrictive in production mode.",
                    ALLOW_ALL_RUNNING_BOTS_ENV,
                    allowedBotIds
            );
        }

        if (preflightOnly) {
            log.warn(
                    "[REAL ACTION CONFIG] PREFLIGHT ONLY mode is armed for scope {}. "
                            + "Requested first offers={}, next steps={}. "
                            + "No real submit can be executed while {}=true.",
                    config.scopeLabel(),
                    realOffersRequested,
                    realNextStepsRequested,
                    PREFLIGHT_ONLY_ENV
            );
            return config;
        }

        if (config.productionModeEnabled()) {
            log.warn(
                    "[REAL ACTION CONFIG] PRODUCTION REAL ACTION MODE is armed for scope {}. "
                            + "First offers requested={}, next steps requested={}. "
                            + "The process-wide first-offer one-shot test lock is disabled. "
                            + "Backend quota/idempotency and per-run action limits remain enforced.",
                    config.scopeLabel(),
                    realOffersRequested,
                    realNextStepsRequested
            );
            return config;
        }

        log.warn(
                "[REAL ACTION CONFIG] CONTROLLED REAL ACTION MODE is armed for bot allowlist {}. "
                        + "First offers requested={}, next steps requested={}. "
                        + "Per-run limits and the process-wide first-offer one-shot test lock remain enforced per bot.",
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
                && hasConfiguredActionScope()
                && (realOffersRequested || realNextStepsRequested);
    }

    public boolean firstOfferOneShotTestModeEnabled() {
        return !productionModeEnabled();
    }

    public boolean isArmedFor(Long botId) {
        if (!confirmationValid
                || botId == null
                || botId <= 0L) {
            return false;
        }

        if (productionModeRequested
                && !productionConfirmationValid) {
            return false;
        }

        if (allowAllRunningBotsRequested) {
            return productionModeRequested
                    && productionConfirmationValid;
        }

        return allowedBotIds.contains(botId);
    }

    private boolean hasConfiguredActionScope() {
        if (allowAllRunningBotsRequested) {
            return productionModeRequested
                    && productionConfirmationValid;
        }

        return !allowedBotIds.isEmpty();
    }

    private String scopeLabel() {
        if (allowAllRunningBotsRequested) {
            return "ALL RUNNING BOTS";
        }

        return allowedBotIds.toString();
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
                            + "The entire explicit bot allowlist is disabled.",
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
