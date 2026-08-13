package pl.flipbot.playwright.worker;

import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

@Slf4j
public record ScheduledRealActionConfig(
        boolean realOffersRequested,
        boolean realNextStepsRequested,
        Long allowedBotId,
        boolean confirmationValid
) {

    private static final String REAL_OFFERS_ENV =
            "FLIPBOT_REAL_OFFERS_ENABLED";

    private static final String REAL_NEXT_STEPS_ENV =
            "FLIPBOT_REAL_NEXT_STEPS_ENABLED";

    private static final String BOT_ID_ENV =
            "FLIPBOT_REAL_ACTION_BOT_ID";

    private static final String CONFIRM_ENV =
            "FLIPBOT_REAL_ACTION_CONFIRM";

    private static final String EXPECTED_CONFIRMATION =
            "I_UNDERSTAND_REAL_ACTIONS";

    public static ScheduledRealActionConfig fromEnvironment() {
        boolean realOffersRequested =
                readBoolean(REAL_OFFERS_ENV, false);

        boolean realNextStepsRequested =
                readBoolean(REAL_NEXT_STEPS_ENV, false);

        Long allowedBotId =
                readPositiveLongOrNull(BOT_ID_ENV);

        String confirmation =
                System.getenv(CONFIRM_ENV);

        boolean confirmationValid =
                EXPECTED_CONFIRMATION.equals(confirmation);

        ScheduledRealActionConfig config =
                new ScheduledRealActionConfig(
                        realOffersRequested,
                        realNextStepsRequested,
                        allowedBotId,
                        confirmationValid
                );

        if (!realOffersRequested && !realNextStepsRequested) {
            log.info(
                    "[REAL ACTION CONFIG] Scheduled real actions are disabled. "
                            + "All scheduler jobs remain DRY RUN."
            );

            return config;
        }

        if (allowedBotId == null || !confirmationValid) {
            log.error(
                    "[REAL ACTION CONFIG] Real actions were requested but the safety gate is incomplete. "
                            + "Required: positive {} and exact {} token. "
                            + "Effective mode remains DRY RUN.",
                    BOT_ID_ENV,
                    CONFIRM_ENV
            );

            return config;
        }

        log.warn(
                "[REAL ACTION CONFIG] CONTROLLED REAL ACTION MODE is armed for bot {} only. "
                        + "First offers requested={}, next steps requested={}. "
                        + "Per-run limits remain hard-capped by the executor.",
                allowedBotId,
                realOffersRequested,
                realNextStepsRequested
        );

        return config;
    }

    public boolean realOffersEnabledFor(Long botId) {
        return realOffersRequested
                && isArmedFor(botId);
    }

    public boolean realNextStepsEnabledFor(Long botId) {
        return realNextStepsRequested
                && isArmedFor(botId);
    }

    public boolean isArmedFor(Long botId) {
        return confirmationValid
                && allowedBotId != null
                && Objects.equals(allowedBotId, botId);
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
