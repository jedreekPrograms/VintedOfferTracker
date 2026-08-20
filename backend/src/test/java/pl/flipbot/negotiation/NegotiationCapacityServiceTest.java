package pl.flipbot.negotiation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.flipbot.bot.Bot;
import pl.flipbot.bot.BotRepository;
import pl.flipbot.bot.configuration.BotConfiguration;
import pl.flipbot.negotiation.quota.DailyOfferQuotaService;
import pl.flipbot.negotiation.quota.dto.DailyOfferQuotaResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NegotiationCapacityServiceTest {

    private static final long BOT_ID = 3L;

    private BotRepository botRepository;
    private DailyOfferQuotaService dailyOfferQuotaService;
    private NegotiationCapacityService service;
    private Bot bot;

    @BeforeEach
    void setUp() {
        botRepository = mock(BotRepository.class);
        dailyOfferQuotaService = mock(DailyOfferQuotaService.class);
        service = new NegotiationCapacityService(
                botRepository,
                dailyOfferQuotaService
        );

        BotConfiguration configuration = BotConfiguration.builder()
                .dailyNegotiationBudget(25)
                .negotiationSteps(new ArrayList<>(
                        List.of(
                                NegotiationStep.builder().stepNumber(1).build(),
                                NegotiationStep.builder().stepNumber(2).build(),
                                NegotiationStep.builder().stepNumber(3).build(),
                                NegotiationStep.builder().stepNumber(4).build(),
                                NegotiationStep.builder().stepNumber(5).build()
                        )
                ))
                .build();

        bot = Bot.builder()
                .id(BOT_ID)
                .configuration(configuration)
                .build();

        configuration.setBot(bot);
        when(botRepository.findById(BOT_ID)).thenReturn(Optional.of(bot));
    }

    @Test
    void capacityUsesActualRemainingQuotaWithoutReservingFutureSteps() {
        when(dailyOfferQuotaService.getQuota(BOT_ID))
                .thenReturn(new DailyOfferQuotaResponse(25, 7, 18));

        assertEquals(
                18,
                service.calculateCapacity(BOT_ID).allowedNewNegotiations()
        );
    }

    @Test
    void exhaustedQuotaBlocksNewNegotiations() {
        when(dailyOfferQuotaService.getQuota(BOT_ID))
                .thenReturn(new DailyOfferQuotaResponse(25, 25, 0));

        assertEquals(
                0,
                service.calculateCapacity(BOT_ID).allowedNewNegotiations()
        );
    }

    @Test
    void missingNegotiationStepsFailsClosedBeforeQuotaLookup() {
        bot.getConfiguration().setNegotiationSteps(new ArrayList<>());

        assertEquals(
                0,
                service.calculateCapacity(BOT_ID).allowedNewNegotiations()
        );

        verifyNoInteractions(dailyOfferQuotaService);
    }
}
