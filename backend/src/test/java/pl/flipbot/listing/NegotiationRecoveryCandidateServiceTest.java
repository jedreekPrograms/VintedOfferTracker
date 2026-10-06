package pl.flipbot.listing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class NegotiationRecoveryCandidateServiceTest {

    private final NegotiationRecoveryCandidateService service =
            new NegotiationRecoveryCandidateService(
                    mock(ListingRepository.class)
            );

    @Test
    void onlyTechnicalTerminalStatusesAreWatched() {
        assertTrue(service.isWatchedTerminalStatus(ListingStatus.REJECTED));
        assertTrue(service.isWatchedTerminalStatus(ListingStatus.EXPIRED));
        assertTrue(service.isWatchedTerminalStatus(ListingStatus.UNAVAILABLE));
        assertTrue(
                service.isWatchedTerminalStatus(
                        ListingStatus.CONTACT_UNAVAILABLE
                )
        );

        assertFalse(
                service.isWatchedTerminalStatus(
                        ListingStatus.ACTION_REQUIRED
                )
        );
        assertFalse(service.isWatchedTerminalStatus(ListingStatus.PURCHASED));
        assertFalse(
                service.isWatchedTerminalStatus(
                        ListingStatus.SKIPPED_BY_USER
                )
        );
    }
}
