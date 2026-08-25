package pl.flipbot.probe;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import pl.flipbot.bot.Bot;
import pl.flipbot.bot.BotRepository;
import pl.flipbot.bot.BotStatus;
import pl.flipbot.listing.Listing;
import pl.flipbot.listing.ListingRepository;
import pl.flipbot.listing.ListingStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class PriceProbeRepositoryRecoveryTest {

    @Autowired
    private BotRepository botRepository;

    @Autowired
    private ListingRepository listingRepository;

    @Autowired
    private PriceProbeRepository priceProbeRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void atomicallyRecoversOnlyExpiredClaimedRowsWithoutFreeingTheirSlots() {
        String suffix = UUID.randomUUID().toString();

        Bot sourceBot = bot("source-" + suffix);
        Bot staleProbeBot = bot("stale-probe-" + suffix);
        Bot freshProbeBot = bot("fresh-probe-" + suffix);
        Bot sentProbeBot = bot("sent-probe-" + suffix);
        botRepository.saveAllAndFlush(List.of(
                sourceBot,
                staleProbeBot,
                freshProbeBot,
                sentProbeBot
        ));

        Listing source = Listing.builder()
                .listingId("market-" + suffix)
                .title("Price probe recovery source")
                .url("https://source.example/items/" + suffix)
                .originalPrice(new BigDecimal("1600.00"))
                .currentPrice(new BigDecimal("1300.00"))
                .currentStep(1)
                .awaitingSellerResponse(true)
                .status(ListingStatus.NEGOTIATING)
                .bot(sourceBot)
                .build();
        source = listingRepository.saveAndFlush(source);

        LocalDateTime now = LocalDateTime.now();

        PriceProbe staleClaim = probe(
                staleProbeBot,
                source,
                PriceProbeStatus.CLAIMED,
                now.minusHours(1),
                null
        );
        PriceProbe freshClaim = probe(
                freshProbeBot,
                source,
                PriceProbeStatus.CLAIMED,
                now.minusMinutes(5),
                null
        );
        PriceProbe sent = probe(
                sentProbeBot,
                source,
                PriceProbeStatus.SENT,
                now.minusHours(1),
                now.minusMinutes(50)
        );

        priceProbeRepository.saveAllAndFlush(List.of(
                staleClaim,
                freshClaim,
                sent
        ));

        String recoveryReason =
                "Unreported probe may already have been sent; no automatic retry.";

        int recovered = priceProbeRepository.transitionStaleClaimsToUnknown(
                PriceProbeStatus.CLAIMED,
                PriceProbeStatus.UNKNOWN,
                now.minusMinutes(30),
                now,
                recoveryReason
        );

        assertEquals(1, recovered);

        entityManager.flush();
        entityManager.clear();

        PriceProbe recoveredStale = priceProbeRepository
                .findById(staleClaim.getId())
                .orElseThrow();
        PriceProbe untouchedFresh = priceProbeRepository
                .findById(freshClaim.getId())
                .orElseThrow();
        PriceProbe untouchedSent = priceProbeRepository
                .findById(sent.getId())
                .orElseThrow();

        assertEquals(PriceProbeStatus.UNKNOWN, recoveredStale.getStatus());
        assertNotNull(recoveredStale.getCompletedAt());
        assertEquals(recoveryReason, recoveredStale.getFailureReason());

        assertEquals(PriceProbeStatus.CLAIMED, untouchedFresh.getStatus());
        assertNull(untouchedFresh.getCompletedAt());

        assertEquals(PriceProbeStatus.SENT, untouchedSent.getStatus());
        assertNotNull(untouchedSent.getCompletedAt());

        // UNKNOWN is deliberately still a reserved slot and the bot/listing
        // pair remains present. Recovery must never turn an ambiguous real
        // marketplace action into permission to send another message.
        assertEquals(3L, priceProbeRepository.countReservedSlots(source.getId()));
        assertTrue(
                priceProbeRepository.existsByProbeBot_IdAndSourceListing_Id(
                        staleProbeBot.getId(),
                        source.getId()
                )
        );
    }

    private Bot bot(String name) {
        return Bot.builder()
                .name(name)
                .status(BotStatus.RUNNING)
                .marketStatsObserver(false)
                .build();
    }

    private PriceProbe probe(
            Bot probeBot,
            Listing source,
            PriceProbeStatus status,
            LocalDateTime claimedAt,
            LocalDateTime completedAt
    ) {
        return PriceProbe.builder()
                .probeBot(probeBot)
                .sourceListing(source)
                .referenceOfferPrice(new BigDecimal("1300.00"))
                .probePrice(new BigDecimal("900.00"))
                .message("Test probe message")
                .status(status)
                .claimedAt(claimedAt)
                .completedAt(completedAt)
                .build();
    }
}
