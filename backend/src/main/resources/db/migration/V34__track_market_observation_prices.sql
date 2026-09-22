ALTER TABLE market_listing_observation
    ADD COLUMN first_seen_price NUMERIC(38, 2),
    ADD COLUMN latest_price NUMERIC(38, 2),
    ADD COLUMN lowest_seen_price NUMERIC(38, 2),
    ADD COLUMN highest_seen_price NUMERIC(38, 2);

CREATE INDEX idx_market_listing_observation_model_published_price
    ON market_listing_observation(model_id, published_at)
    WHERE latest_price IS NOT NULL OR first_seen_price IS NOT NULL;
