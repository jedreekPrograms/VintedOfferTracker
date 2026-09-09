ALTER TABLE market_listing_observation
    ADD COLUMN published_at TIMESTAMP;

-- Existing observations predate publication-time enrichment. Keep their
-- previous first-seen semantics instead of rewriting historical statistics.
UPDATE market_listing_observation
SET published_at = first_seen_at
WHERE published_at IS NULL;

CREATE INDEX idx_market_listing_observation_model_effective_published_at
    ON market_listing_observation (
        model_id,
        (COALESCE(published_at, first_seen_at))
    );
