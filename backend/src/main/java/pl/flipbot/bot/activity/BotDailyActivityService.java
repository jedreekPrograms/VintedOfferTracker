package pl.flipbot.bot.activity;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.flipbot.bot.activity.dto.BotDailyActivityResponse;
import pl.flipbot.listing.ListingRepository;
import pl.flipbot.listing.ListingStatus;
import pl.flipbot.negotiation.audit.RealActionAudit;
import pl.flipbot.negotiation.audit.RealActionAuditOutcome;
import pl.flipbot.negotiation.audit.RealActionAuditRepository;
import pl.flipbot.negotiation.guard.RealActionType;
import pl.flipbot.negotiation.quota.DailyOfferQuotaService;
import pl.flipbot.negotiation.quota.dto.DailyOfferQuotaResponse;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BotDailyActivityService {

    private static final ZoneId ACTIVITY_ZONE =
            ZoneId.of("Europe/Warsaw");

    private final ListingRepository listingRepository;
    private final RealActionAuditRepository realActionAuditRepository;
    private final DailyOfferQuotaService dailyOfferQuotaService;

    public BotDailyActivityResponse getToday(
            Long botId
    ) {
        LocalDate today = LocalDate.now(ACTIVITY_ZONE);
        LocalDateTime dayStart = today.atStartOfDay();
        LocalDateTime nextDayStart = today.plusDays(1).atStartOfDay();

        DailyOfferQuotaResponse quota =
                dailyOfferQuotaService.getQuota(botId);

        List<RealActionAudit> audits = realActionAuditRepository
                .findAllByBotIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        botId,
                        dayStart,
                        nextDayStart
                );

        List<RealActionAudit> confirmedAudits = audits.stream()
                .filter(audit -> audit.getOutcome() == RealActionAuditOutcome.CONFIRMED)
                .toList();

        int ambiguousActionsToday = Math.toIntExact(
                audits.stream()
                        .filter(audit -> audit.getOutcome() == RealActionAuditOutcome.AMBIGUOUS)
                        .count()
        );

        Set<Long> negotiationsStartedToday = new HashSet<>();
        for (RealActionAudit audit : confirmedAudits) {
            if (audit.getActionType() == RealActionType.FIRST_OFFER) {
                negotiationsStartedToday.add(audit.getBackendListingId());
            }
        }

        int nextStepsInNegotiationsStartedToday = 0;
        int nextStepsInOlderNegotiations = 0;

        for (RealActionAudit audit : confirmedAudits) {
            if (audit.getActionType() != RealActionType.NEXT_STEP) {
                continue;
            }

            if (negotiationsStartedToday.contains(audit.getBackendListingId())) {
                nextStepsInNegotiationsStartedToday++;
            } else {
                nextStepsInOlderNegotiations++;
            }
        }

        int confirmedActionsToday = confirmedAudits.size();
        int auditedQuotaFloor = confirmedActionsToday + ambiguousActionsToday;
        int usedSlotsWithoutAuditYet = Math.max(
                quota.used() - auditedQuotaFloor,
                0
        );

        int activeNegotiations = Math.toIntExact(
                listingRepository.countByBotIdAndStatus(
                        botId,
                        ListingStatus.NEGOTIATING
                )
        );

        return new BotDailyActivityResponse(
                botId,
                today,
                ACTIVITY_ZONE.getId(),
                quota.limit(),
                quota.used(),
                quota.remaining(),
                activeNegotiations,
                negotiationsStartedToday.size(),
                nextStepsInNegotiationsStartedToday,
                nextStepsInOlderNegotiations,
                confirmedActionsToday,
                ambiguousActionsToday,
                usedSlotsWithoutAuditYet
        );
    }
}
