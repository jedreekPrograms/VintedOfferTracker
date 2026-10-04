package pl.flipbot.negotiation.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.flipbot.bot.Bot;
import pl.flipbot.bot.configuration.BotConfiguration;
import pl.flipbot.listing.Listing;
import pl.flipbot.negotiation.NegotiationStep;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NegotiationStrategySnapshotServiceTest {

    @Test
    void pinnedSnapshotDoesNotChangeWhenLiveStrategyIsEdited() {
        NegotiationStep step = NegotiationStep.builder()
                .stepNumber(1)
                .offerPrice(new BigDecimal("1200.00"))
                .maxAcceptedCounterOffer(new BigDecimal("1300.00"))
                .message("stara wiadomość")
                .build();

        BotConfiguration configuration = BotConfiguration.builder()
                .autoRaiseOfferToVintedMinimum(true)
                .maxAutomaticOffer(new BigDecimal("1450.00"))
                .negotiationStrategyVersion(3)
                .negotiationSteps(new ArrayList<>(List.of(step)))
                .build();
        step.setConfiguration(configuration);

        Bot bot = Bot.builder()
                .id(7L)
                .configuration(configuration)
                .build();
        configuration.setBot(bot);

        Listing listing = Listing.builder()
                .id(99L)
                .bot(bot)
                .build();

        NegotiationStrategySnapshotService service =
                new NegotiationStrategySnapshotService(new ObjectMapper());

        assertTrue(service.pinIfMissing(listing));
        assertEquals(3, listing.getNegotiationStrategyVersion());

        configuration.setNegotiationStrategyVersion(4);
        configuration.setMaxAutomaticOffer(new BigDecimal("1600.00"));
        step.setOfferPrice(new BigDecimal("1350.00"));
        step.setMessage("nowa wiadomość");

        NegotiationStrategySnapshot snapshot = service.read(listing);

        assertEquals(3, snapshot.version());
        assertEquals(
                0,
                new BigDecimal("1450.00").compareTo(snapshot.maxAutomaticOffer())
        );
        assertEquals(
                0,
                new BigDecimal("1200.00").compareTo(
                        snapshot.negotiationSteps().get(0).offerPrice()
                )
        );
        assertEquals(
                "stara wiadomość",
                snapshot.negotiationSteps().get(0).message()
        );
    }
}
