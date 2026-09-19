package pl.flipbot.playwright.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds deterministic per-product execution plans from the bot payload.
 * Catalog work uses only active additional products; negotiation work keeps
 * inactive products so conversations started before deactivation retain their
 * original strategy after any Playwright process restart.
 */
public final class BotProductExecutionPlan {

    private BotProductExecutionPlan() {
    }

    public static List<Target> activeCatalogTargets(BotDetailsDto bot) {
        BotConfigurationDto main = requireMain(bot);
        List<Target> result = new ArrayList<>();
        result.add(new Target(null, main));

        List<BotAdditionalTargetDto> extras = bot.getAdditionalTargets();
        if (extras == null || extras.isEmpty()) {
            return List.copyOf(result);
        }

        for (BotAdditionalTargetDto extra : extras) {
            if (extra != null
                    && extra.getAdditionalTargetId() != null
                    && Boolean.TRUE.equals(extra.getActive())) {
                result.add(new Target(
                        extra.getAdditionalTargetId(),
                        extra
                ));
            }
        }

        if (result.size() > 5) {
            throw new IllegalStateException(
                    "Bot has more than 4 active additional products"
            );
        }

        return List.copyOf(result);
    }

    public static List<Target> negotiationTargets(BotDetailsDto bot) {
        BotConfigurationDto main = requireMain(bot);
        List<Target> result = new ArrayList<>();
        result.add(new Target(null, main));

        List<BotAdditionalTargetDto> extras = bot.getAdditionalTargets();
        if (extras == null || extras.isEmpty()) {
            return List.copyOf(result);
        }

        for (BotAdditionalTargetDto extra : extras) {
            if (extra == null || extra.getAdditionalTargetId() == null) {
                continue;
            }
            result.add(new Target(
                    extra.getAdditionalTargetId(),
                    extra
            ));
        }

        return List.copyOf(result);
    }

    private static BotConfigurationDto requireMain(BotDetailsDto bot) {
        if (bot == null) {
            throw new IllegalArgumentException("Bot details are required");
        }
        BotConfigurationDto main = bot.getConfiguration();
        if (main == null) {
            throw new IllegalStateException("Bot configuration is missing");
        }
        return main;
    }

    public record Target(
            Long additionalTargetId,
            BotConfigurationDto configuration
    ) {
    }
}
