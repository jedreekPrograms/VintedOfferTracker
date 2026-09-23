package pl.flipbot.marketstats;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketListingPublicationServiceTest {

    @Mock
    private MarketListingObservationRepository observationRepository;

    private MarketListingPublicationService service;

    @BeforeEach
    void setUp() {
        service = new MarketListingPublicationService(observationRepository);
    }

    @Test
    void returnsPersistedPublicationTimesForBoundaryReuse() {
        LocalDateTime firstPublishedAt =
                LocalDateTime.of(2026, 7, 1, 12, 0);
        LocalDateTime secondPublishedAt =
                LocalDateTime.of(2026, 7, 2, 13, 30);

        when(observationRepository.findAllByModel_IdAndPublishedAtIsNotNull(68L))
                .thenReturn(
                        List.of(
                                MarketListingObservation.builder()
                                        .marketplaceListingId("100")
                                        .publishedAt(firstPublishedAt)
                                        .build(),
                                MarketListingObservation.builder()
                                        .marketplaceListingId("200")
                                        .publishedAt(secondPublishedAt)
                                        .build()
                        )
                );

        Map<String, String> result = service.getPublicationTimes(68L);

        assertEquals(firstPublishedAt.toString(), result.get("100"));
        assertEquals(secondPublishedAt.toString(), result.get("200"));
        assertEquals(2, result.size());
    }
}
