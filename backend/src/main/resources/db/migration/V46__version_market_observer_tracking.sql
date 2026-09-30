ALTER TABLE market_model_scan_state
    ADD COLUMN IF NOT EXISTS tracking_generation INTEGER NOT NULL DEFAULT 1;

ALTER TABLE market_listing_observation
    ADD COLUMN IF NOT EXISTS tracking_generation INTEGER NOT NULL DEFAULT 1;

ALTER TABLE market_listing_observation
    DROP CONSTRAINT IF EXISTS uk_market_listing_observation_model_listing;

CREATE UNIQUE INDEX IF NOT EXISTS uk_market_listing_observation_model_generation_listing
    ON market_listing_observation (
        model_id,
        tracking_generation,
        marketplace_listing_id
    );

DROP INDEX IF EXISTS idx_market_listing_observation_model_published_at;
CREATE INDEX idx_market_listing_observation_model_published_at
    ON market_listing_observation(model_id, tracking_generation, published_at)
    WHERE published_at IS NOT NULL;

DROP INDEX IF EXISTS idx_market_listing_observation_model_published_price;
CREATE INDEX idx_market_listing_observation_model_published_price
    ON market_listing_observation(model_id, tracking_generation, published_at)
    WHERE latest_price IS NOT NULL OR first_seen_price IS NOT NULL;
