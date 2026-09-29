package pl.flipbot.playwright.worker;

import org.junit.Test;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.model.BotDetailsDto;
import pl.flipbot.playwright.negotiation.ExistingNegotiationProcessor;
import pl.flipbot.playwright.processing.CatalogWorkProcessor;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BotRunExecutorCatalogMeasurementTest {

    @Test
    public void normalCatalogRunSkipsOneShotNegotiationMeasurement() {
        BotContext context = contextWithBotId(91_001L);
        CatalogWorkProcessor catalog = mock(CatalogWorkProcessor.class);

        BotRunExecutor executor = new BotRunExecutor(
                context,
                mock(ExistingNegotiationProcessor.class),
                catalog,
                true,
                false
        );

        executor.executeCatalogScan();

        verify(catalog).process(false);
    }

    @Test
    public void armedOneShotCatalogRunKeepsNegotiationMeasurement() {
        BotContext context = contextWithBotId(91_002L);
        CatalogWorkProcessor catalog = mock(CatalogWorkProcessor.class);
        when(catalog.process(true)).thenReturn(false);

        BotRunExecutor executor = new BotRunExecutor(
                context,
                mock(ExistingNegotiationProcessor.class),
                catalog,
                true,
                true
        );

        executor.executeCatalogScan();

        verify(catalog).process(true);
    }

    private BotContext contextWithBotId(Long botId) {
        BotContext context = mock(BotContext.class);
        BotDetailsDto bot = new BotDetailsDto();
        bot.setId(botId);

        when(context.getBot()).thenReturn(bot);

        return context;
    }
}
