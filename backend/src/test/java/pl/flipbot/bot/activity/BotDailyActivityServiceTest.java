package pl.flipbot.bot.activity;

import org.junit.jupiter.api.Test;
import pl.flipbot.bot.activity.dto.BotDailyActivityResponse;
import pl.flipbot.listing.ListingRepository;
import pl.flipbot.listing.ListingStatus;
import pl.flipbot.negotiation.audit.RealActionAudit;
import pl.flipbot.negotiation.audit.RealActionAuditOutcome;
import pl.flipbot.negotiation.audit.RealActionAuditRepository;
import pl.flipbot.negotiation.guard.RealActionType;
import pl.flipbot.negotiation.quota.DailyOfferQuotaService;
import pl.flipbot.negotiation.quota.dto.DailyOfferQuotaResponse;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BotDailyActivityServiceTest {

    @Test
    void splitsTodayNextStepsBetweenNewAndOlderNegotiations() {
        ListingRepository listingRepository = mock(ListingRepository.class);
        RealActionAuditRepository auditRepository = mock(RealActionAuditRepository.class);
        DailyOfferQuotaService quotaService = mock(DailyOfferQuotaService.class);

        when(quotaService.getQuota(4L)).thenReturn(
                new DailyOfferQuotaResponse(25, 5, 20)
        );
        when(listingRepository.countByBotIdAndStatus(
                4L,
                ListingStatus.NEGOTIATING
        )).thenReturn(3L);
        when(auditRepository.findAllByBotIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(4L),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(List.of(
                audit(101L, RealActionType.FIRST_OFFER, RealActionAuditOutcome.CONFIRMED),
                audit(101L, RealActionType.NEXT_STEP, RealActionAuditOutcome.CONFIRMED),
                audit(202L, RealActionType.NEXT_STEP, RealActionAuditOutcome.CONFIRMED),
                audit(303L, RealActionType.NEXT_STEP, RealActionAuditOutcome.AMBIGUOUS)
        ));

        BotDailyActivityService service = new BotDailyActivityService(
                listingRepository,
                auditRepository,
                quotaService
        );

        BotDailyActivityResponse response = service.getToday(4L);

        assertEquals("Europe/Warsaw", response.timeZone());
        assertEquals(25, response.dailyLimit());
        assertEquals(5, response.dailyLimitUsed());
        assertEquals(20, response.dailyLimitRemaining());
        assertEquals(3, response.activeNegotiations());
        assertEquals(1, response.newNegotiationsToday());
        assertEquals(1, response.nextStepsInNegotiationsStartedToday());
        assertEquals(1, response.nextStepsInOlderNegotiations());
        assertEquals(3, response.confirmedActionsToday());
        assertEquals(1, response.ambiguousActionsToday());
        assertEquals(1, response.usedSlotsWithoutAuditYet());
    }

    private RealActionAudit audit(
            Long backendListingId,
            RealActionType actionType,
            RealActionAuditOutcome outcome
    ) {
        return RealActionAudit.builder()
                .botId(4L)
                .backendListingId(backendListingId)
                .actionType(actionType)
                .outcome(outcome)
                .build();
    }
}
