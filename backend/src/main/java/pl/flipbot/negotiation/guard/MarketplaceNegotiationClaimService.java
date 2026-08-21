package pl.flipbot.negotiation.guard;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import pl.flipbot.listing.Listing;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MarketplaceNegotiationClaimService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Atomically claims a marketplace listing before a FIRST_OFFER action.
     * The database unique constraint is the cross-worker/cross-bot lock; using
     * INSERT ... ON CONFLICT keeps a losing transaction usable instead of
     * poisoning it with a constraint exception.
     */
    public ClaimResult acquire(Listing listing, UUID requestId) {
        Objects.requireNonNull(listing, "Listing cannot be null");
        Objects.requireNonNull(requestId, "Request id cannot be null");

        String marketplace = marketplace(listing);
        String marketplaceListingId = requiredMarketplaceListingId(listing);

        int inserted = jdbcTemplate.update(
                """
                INSERT INTO marketplace_negotiation_claim (
                    marketplace,
                    marketplace_listing_id,
                    owner_bot_id,
                    owner_listing_id,
                    request_id,
                    claimed_at
                ) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (marketplace, marketplace_listing_id) DO NOTHING
                """,
                marketplace,
                marketplaceListingId,
                listing.getBot().getId(),
                listing.getId(),
                requestId
        );

        if (inserted == 1) {
            return new ClaimResult(
                    true,
                    listing.getBot().getId(),
                    listing.getId(),
                    requestId,
                    LocalDateTime.now()
            );
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT owner_bot_id, owner_listing_id, request_id, claimed_at
                FROM marketplace_negotiation_claim
                WHERE marketplace = ?
                  AND marketplace_listing_id = ?
                """,
                marketplace,
                marketplaceListingId
        );

        if (rows.size() != 1) {
            throw new IllegalStateException(
                    "Marketplace negotiation claim conflict was reported, but the durable claim could not be read for "
                            + marketplace + "/" + marketplaceListingId
            );
        }

        Map<String, Object> row = rows.get(0);
        Long ownerBotId = ((Number) row.get("owner_bot_id")).longValue();
        Long ownerListingId = ((Number) row.get("owner_listing_id")).longValue();
        UUID existingRequestId = toUuid(row.get("request_id"));
        LocalDateTime claimedAt = toLocalDateTime(row.get("claimed_at"));

        boolean exactReplay = Objects.equals(ownerBotId, listing.getBot().getId())
                && Objects.equals(ownerListingId, listing.getId())
                && Objects.equals(existingRequestId, requestId);

        return new ClaimResult(
                exactReplay,
                ownerBotId,
                ownerListingId,
                existingRequestId,
                claimedAt
        );
    }

    /**
     * Releases only a claim that belongs to this exact FIRST_OFFER request.
     * Call this solely when the listing is still DISCOVERED, i.e. the caller
     * knows the real submit was never attempted. Ambiguous/post-submit claims
     * deliberately survive forever as idempotency tombstones.
     */
    public void releaseBeforeSubmit(Listing listing, UUID requestId) {
        String marketplace = marketplace(listing);
        String marketplaceListingId = requiredMarketplaceListingId(listing);

        int deleted = jdbcTemplate.update(
                """
                DELETE FROM marketplace_negotiation_claim
                WHERE marketplace = ?
                  AND marketplace_listing_id = ?
                  AND owner_bot_id = ?
                  AND owner_listing_id = ?
                  AND request_id = ?
                """,
                marketplace,
                marketplaceListingId,
                listing.getBot().getId(),
                listing.getId(),
                requestId
        );

        if (deleted != 1) {
            throw new IllegalStateException(
                    "Could not release the exact marketplace negotiation claim for listing "
                            + listing.getId() + " and request " + requestId
            );
        }
    }

    private String marketplace(Listing listing) {
        if (listing.getBot() == null
                || listing.getBot().getConfiguration() == null
                || listing.getBot().getConfiguration().getMarketplace() == null) {
            throw new IllegalStateException(
                    "Cannot claim marketplace listing because bot marketplace is missing"
            );
        }
        return listing.getBot().getConfiguration().getMarketplace().name();
    }

    private String requiredMarketplaceListingId(Listing listing) {
        if (listing.getListingId() == null || listing.getListingId().isBlank()) {
            throw new IllegalStateException(
                    "Cannot claim marketplace listing because marketplace listing id is missing"
            );
        }
        return listing.getListingId();
    }

    private UUID toUuid(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof UUID uuid) {
            return uuid;
        }
        return UUID.fromString(value.toString());
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return LocalDateTime.parse(value.toString().replace(' ', 'T'));
    }

    public record ClaimResult(
            boolean acquired,
            Long ownerBotId,
            Long ownerListingId,
            UUID requestId,
            LocalDateTime claimedAt
    ) {
    }
}
