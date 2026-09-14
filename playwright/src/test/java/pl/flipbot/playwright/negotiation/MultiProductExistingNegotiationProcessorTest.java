package pl.flipbot.playwright.negotiation;

import org.junit.After;
import org.junit.Test;
import pl.flipbot.playwright.model.BotAdditionalTargetDto;
import pl.flipbot.playwright.model.BotConfigurationDto;
import pl.flipbot.playwright.model.BotDetailsDto;
import pl.flipbot.playwright.model.BotProductExecutionPlan;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class MultiProductExistingNegotiationProcessorTest {

    private static final long BOT_ID = 41L;
    private static final long OTHER_BOT_ID = 42L;

    @After
    public void tearDown() {
        MultiProductExistingNegotiationProcessor.resetRotationForTests(BOT_ID);
        MultiProductExistingNegotiationProcessor.resetRotationForTests(OTHER_BOT_ID);
    }

    @Test
    public void rotatesMainFourActiveAndOneInactiveNegotiatingProductFairly() {
        BotDetailsDto bot = botWithProducts(
                BOT_ID,
                extra(1L, true),
                extra(2L, true),
                extra(3L, true),
                extra(4L, true),
                extra(99L, false)
        );

        List<BotProductExecutionPlan.Target> targets =
                BotProductExecutionPlan.negotiationTargets(bot);

        assertEquals(
                List.of("MAIN", "1", "2", "3", "4", "99"),
                labels(MultiProductExistingNegotiationProcessor
                        .orderedTargetsForRun(BOT_ID, targets))
        );
        assertEquals(
                List.of("1", "2", "3", "4", "99", "MAIN"),
                labels(MultiProductExistingNegotiationProcessor
                        .orderedTargetsForRun(BOT_ID, targets))
        );
        assertEquals(
                List.of("2", "3", "4", "99", "MAIN", "1"),
                labels(MultiProductExistingNegotiationProcessor
                        .orderedTargetsForRun(BOT_ID, targets))
        );
        assertEquals(
                List.of("3", "4", "99", "MAIN", "1", "2"),
                labels(MultiProductExistingNegotiationProcessor
                        .orderedTargetsForRun(BOT_ID, targets))
        );
        assertEquals(
                List.of("4", "99", "MAIN", "1", "2", "3"),
                labels(MultiProductExistingNegotiationProcessor
                        .orderedTargetsForRun(BOT_ID, targets))
        );
        assertEquals(
                List.of("99", "MAIN", "1", "2", "3", "4"),
                labels(MultiProductExistingNegotiationProcessor
                        .orderedTargetsForRun(BOT_ID, targets))
        );
        assertEquals(
                List.of("MAIN", "1", "2", "3", "4", "99"),
                labels(MultiProductExistingNegotiationProcessor
                        .orderedTargetsForRun(BOT_ID, targets))
        );
    }

    @Test
    public void rotationStateIsIndependentPerBot() {
        List<BotProductExecutionPlan.Target> targets =
                BotProductExecutionPlan.negotiationTargets(
                        botWithProducts(BOT_ID, extra(7L, true))
                );

        assertEquals(
                List.of("MAIN", "7"),
                labels(MultiProductExistingNegotiationProcessor
                        .orderedTargetsForRun(BOT_ID, targets))
        );
        assertEquals(
                List.of("7", "MAIN"),
                labels(MultiProductExistingNegotiationProcessor
                        .orderedTargetsForRun(BOT_ID, targets))
        );

        assertEquals(
                List.of("MAIN", "7"),
                labels(MultiProductExistingNegotiationProcessor
                        .orderedTargetsForRun(OTHER_BOT_ID, targets))
        );
    }

    @Test
    public void singleProductLegacyPlanNeverRotatesAwayFromMain() {
        List<BotProductExecutionPlan.Target> targets =
                BotProductExecutionPlan.negotiationTargets(
                        botWithProducts(BOT_ID)
                );

        assertEquals(
                List.of("MAIN"),
                labels(MultiProductExistingNegotiationProcessor
                        .orderedTargetsForRun(BOT_ID, targets))
        );
        assertEquals(
                List.of("MAIN"),
                labels(MultiProductExistingNegotiationProcessor
                        .orderedTargetsForRun(BOT_ID, targets))
        );
    }

    private BotDetailsDto botWithProducts(
            Long botId,
            BotAdditionalTargetDto... extras
    ) {
        BotDetailsDto bot = new BotDetailsDto();
        bot.setId(botId);

        BotConfigurationDto main = new BotConfigurationDto();
        main.setBrand("MAIN");
        main.setModel("MAIN");
        bot.setConfiguration(main);

        bot.setAdditionalTargets(new ArrayList<>(List.of(extras)));
        return bot;
    }

    private BotAdditionalTargetDto extra(Long id, boolean active) {
        BotAdditionalTargetDto extra = new BotAdditionalTargetDto();
        extra.setAdditionalTargetId(id);
        extra.setActive(active);
        extra.setBrand("Brand " + id);
        extra.setModel("Model " + id);
        return extra;
    }

    private List<String> labels(List<BotProductExecutionPlan.Target> targets) {
        return targets.stream()
                .map(target -> target.additionalTargetId() == null
                        ? "MAIN"
                        : target.additionalTargetId().toString())
                .toList();
    }
}
