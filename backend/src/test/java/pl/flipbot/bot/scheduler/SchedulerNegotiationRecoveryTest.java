package pl.flipbot.bot.scheduler;

import org.junit.jupiter.api.Test;
import pl.flipbot.bot.Bot;
import pl.flipbot.bot.BotRepository;
import pl.flipbot.bot.BotStatus;
import pl.flipbot.bot.runtime.BotSessionPreviewService;
import pl.flipbot.listing.ListingRepository;
import pl.flipbot.listing.ListingStatus;
import pl.flipbot.listing.NegotiationRecoveryCandidateService;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SchedulerNegotiationRecoveryTest {

    @Test
    void dueTerminalRecoveryCandidateKeepsNegotiationCheckScheduled() {
        BotRepository bots = mock(BotRepository.class);
        ListingRepository listings = mock(ListingRepository.class);
        NegotiationRecoveryCandidateService recovery =
                mock(NegotiationRecoveryCandidateService.class);

        Bot bot = Bot.builder()
                .id(3L)
                .name("S25")
                .status(BotStatus.RUNNING)
                .build();

        when(bots.findByStatus(BotStatus.RUNNING))
                .thenReturn(List.of(bot));
        when(listings.findDistinctBotIdsByStatusAndBotIdIn(
                ListingStatus.NEGOTIATING,
                List.of(3L)
        )).thenReturn(List.of());
        when(recovery.findBotIdsWithDueCandidates(List.of(3L)))
                .thenReturn(Set.of(3L));

        SchedulerRunningBotService service =
                new SchedulerRunningBotService(
                        bots,
                        listings,
                        new BotSessionPreviewService(),
                        recovery
                );

        assertTrue(
                service.getRunningBots()
                        .getFirst()
                        .isHasActiveNegotiations()
        );
    }
}
