package pl.flipbot.playwright.model;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

public class BotProductExecutionPlanTest {

    @Test
    public void zeroAdditionalProductsKeepsTheLegacySingleProductPlan() {
        BotDetailsDto bot = botWithMain();
        bot.setAdditionalTargets(List.of());

        List<BotProductExecutionPlan.Target> catalog =
                BotProductExecutionPlan.activeCatalogTargets(bot);
        List<BotProductExecutionPlan.Target> negotiations =
                BotProductExecutionPlan.negotiationTargets(bot);

        assertEquals(1, catalog.size());
        assertEquals(1, negotiations.size());
        assertNull(catalog.get(0).additionalTargetId());
        assertNull(negotiations.get(0).additionalTargetId());
        assertSame(bot.getConfiguration(), catalog.get(0).configuration());
        assertSame(bot.getConfiguration(), negotiations.get(0).configuration());
    }

    @Test
    public void nullAdditionalProductsAlsoKeepsTheLegacySingleProductPlan() {
        BotDetailsDto bot = botWithMain();
        bot.setAdditionalTargets(null);

        assertEquals(
                1,
                BotProductExecutionPlan.activeCatalogTargets(bot).size()
        );
        assertEquals(
                1,
                BotProductExecutionPlan.negotiationTargets(bot).size()
        );
    }

    @Test
    public void catalogScansOnlyActiveAdditionalProducts() {
        BotDetailsDto bot = botWithMain();
        BotAdditionalTargetDto active = extra(11L, true, "Active");
        BotAdditionalTargetDto inactive = extra(12L, false, "Inactive");
        bot.setAdditionalTargets(List.of(active, inactive));

        List<BotProductExecutionPlan.Target> targets =
                BotProductExecutionPlan.activeCatalogTargets(bot);

        assertEquals(2, targets.size());
        assertNull(targets.get(0).additionalTargetId());
        assertEquals(Long.valueOf(11L), targets.get(1).additionalTargetId());
        assertSame(active, targets.get(1).configuration());
    }

    @Test
    public void freshPayloadKeepsInactiveProductForExistingNegotiations() {
        BotDetailsDto freshlyLoadedBot = botWithMain();
        BotAdditionalTargetDto inactive = extra(77L, false, "Historical");
        freshlyLoadedBot.setAdditionalTargets(List.of(inactive));

        List<BotProductExecutionPlan.Target> targets =
                BotProductExecutionPlan.negotiationTargets(freshlyLoadedBot);

        assertEquals(2, targets.size());
        assertEquals(Long.valueOf(77L), targets.get(1).additionalTargetId());
        assertSame(inactive, targets.get(1).configuration());
    }

    @Test
    public void moreThanFourActiveAdditionalProductsFailsClosed() {
        BotDetailsDto bot = botWithMain();
        bot.setAdditionalTargets(List.of(
                extra(1L, true, "1"),
                extra(2L, true, "2"),
                extra(3L, true, "3"),
                extra(4L, true, "4"),
                extra(5L, true, "5")
        ));

        assertThrows(
                IllegalStateException.class,
                () -> BotProductExecutionPlan.activeCatalogTargets(bot)
        );
    }

    private BotDetailsDto botWithMain() {
        BotDetailsDto bot = new BotDetailsDto();
        bot.setId(5L);

        BotConfigurationDto main = new BotConfigurationDto();
        main.setBrand("Apple");
        main.setModel("iPhone 13");
        bot.setConfiguration(main);
        return bot;
    }

    private BotAdditionalTargetDto extra(
            Long id,
            boolean active,
            String model
    ) {
        BotAdditionalTargetDto extra = new BotAdditionalTargetDto();
        extra.setAdditionalTargetId(id);
        extra.setActive(active);
        extra.setBrand("Apple");
        extra.setModel(model);
        return extra;
    }
}
