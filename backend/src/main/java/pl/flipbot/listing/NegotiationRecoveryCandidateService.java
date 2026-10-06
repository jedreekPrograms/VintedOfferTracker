package pl.flipbot.listing;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class NegotiationRecoveryCandidateService {

    public static final int WATCH_WINDOW_DAYS = 7;
    public static final int RECHECK_INTERVAL_MINUTES = 5;
    public static final int MAX_CANDIDATES_PER_RUN = 12;

    private static final List<ListingStatus> WATCHED_STATUSES =
            List.of(
                    ListingStatus.REJECTED,
                    ListingStatus.EXPIRED,
                    ListingStatus.UNAVAILABLE,
                    ListingStatus.CONTACT_UNAVAILABLE
            );

    private final ListingRepository listingRepository;

    public List<Listing> findDueCandidates(Long botId) {
        LocalDateTime now = LocalDateTime.now();

        return listingRepository.findDueTerminalConversationCandidates(
                botId,
                WATCHED_STATUSES,
                now.minusDays(WATCH_WINDOW_DAYS),
                now.minusMinutes(RECHECK_INTERVAL_MINUTES),
                PageRequest.of(0, MAX_CANDIDATES_PER_RUN)
        );
    }

    public Set<Long> findBotIdsWithDueCandidates(
            Collection<Long> botIds
    ) {
        if (botIds == null || botIds.isEmpty()) {
            return Set.of();
        }

        LocalDateTime now = LocalDateTime.now();

        return Set.copyOf(
                listingRepository
                        .findDistinctBotIdsWithDueTerminalConversationCandidates(
                                botIds,
                                WATCHED_STATUSES,
                                now.minusDays(WATCH_WINDOW_DAYS),
                                now.minusMinutes(RECHECK_INTERVAL_MINUTES)
                        )
        );
    }

    public boolean isWatchedTerminalStatus(ListingStatus status) {
        return WATCHED_STATUSES.contains(status);
    }
}
