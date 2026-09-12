package pl.flipbot.marketstats;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.flipbot.marketstats.dto.MarketListingPublicationBatchRequest;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketListingPublicationService {

    private static final ZoneId MARKET_STATS_ZONE = ZoneId.of("Europe/Warsaw");

    private final MarketListingObservationRepository observationRepository;

    @Transactional(readOnly = true)
    public List<String> getMissingPublicationListingIds(Long modelId) {
        if (modelId == null || modelId <= 0) {
            return List.of();
        }

        return observationRepository.findListingIdsMissingPublishedAt(modelId);
    }

    @Transactional(readOnly = true)
    public Map<String, String> getPublicationTimes(Long modelId) {
        if (modelId == null || modelId <= 0) {
            return Map.of();
        }

        Map<String, String> result = new LinkedHashMap<>();

        for (MarketListingObservation observation
                : observationRepository.findAllByModel_IdAndPublishedAtIsNotNull(modelId)) {
            if (observation == null
                    || observation.getMarketplaceListingId() == null
                    || observation.getMarketplaceListingId().isBlank()
                    || observation.getPublishedAt() == null) {
                continue;
            }

            result.put(
                    observation.getMarketplaceListingId().trim(),
                    observation.getPublishedAt().toString()
            );
        }

        return Map.copyOf(result);
    }

    @Transactional
    public int recordPublicationTimes(
            Long modelId,
            MarketListingPublicationBatchRequest request
    ) {
        Objects.requireNonNull(
                request,
                "Market listing publication request cannot be null"
        );

        Map<String, String> publicationTimes = request.publishedAtByListingId();

        if (publicationTimes == null || publicationTimes.isEmpty()) {
            return 0;
        }

        LocalDateTime latestAccepted = LocalDateTime.now(MARKET_STATS_ZONE)
                .plusMinutes(5L);
        int updated = 0;
        int rejected = 0;

        for (Map.Entry<String, String> entry : publicationTimes.entrySet()) {
            String listingId = normalizeListingId(entry.getKey());
            String rawPublishedAt = entry.getValue();

            if (listingId == null
                    || rawPublishedAt == null
                    || rawPublishedAt.isBlank()) {
                rejected++;
                continue;
            }

            LocalDateTime publishedAt;

            try {
                publishedAt = LocalDateTime.parse(rawPublishedAt.trim());
            } catch (DateTimeException exception) {
                rejected++;
                log.warn(
                        "[MARKET STATS] Ignoring invalid publication timestamp. modelId={}, listingId={}, value='{}'.",
                        modelId,
                        listingId,
                        rawPublishedAt
                );
                continue;
            }

            if (publishedAt.isAfter(latestAccepted)) {
                rejected++;
                log.warn(
                        "[MARKET STATS] Ignoring future publication timestamp. modelId={}, listingId={}, publishedAt={}.",
                        modelId,
                        listingId,
                        publishedAt
                );
                continue;
            }

            updated += observationRepository.updatePublishedAt(
                    modelId,
                    listingId,
                    publishedAt
            );
        }

        log.info(
                "[MARKET STATS] Publication times recorded. modelId={}, requested={}, updated={}, rejected={}.",
                modelId,
                publicationTimes.size(),
                updated,
                rejected
        );

        return updated;
    }

    private String normalizeListingId(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
