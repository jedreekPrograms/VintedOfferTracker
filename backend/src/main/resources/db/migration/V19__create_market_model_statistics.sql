ALTER TABLE bot
    ADD COLUMN market_stats_observer BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE market_model_scan_state (
    model_id BIGINT PRIMARY KEY,
    initialized_at TIMESTAMP NOT NULL,
    baseline_complete_at TIMESTAMP,
    last_scan_at TIMESTAMP NOT NULL,
    last_successful_scan_at TIMESTAMP,
    last_scan_complete BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_market_model_scan_state_model
        FOREIGN KEY (model_id)
        REFERENCES dictionary_model(id)
        ON DELETE CASCADE
);

CREATE TABLE market_listing_observation (
    id BIGSERIAL PRIMARY KEY,
    model_id BIGINT NOT NULL,
    marketplace_listing_id VARCHAR(255) NOT NULL,
    first_seen_at TIMESTAMP NOT NULL,
    last_seen_at TIMESTAMP NOT NULL,
    baseline BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_market_listing_observation_model
        FOREIGN KEY (model_id)
        REFERENCES dictionary_model(id)
        ON DELETE CASCADE,
    CONSTRAINT uk_market_listing_observation_model_listing
        UNIQUE (model_id, marketplace_listing_id)
);

CREATE INDEX idx_market_listing_observation_model_first_seen
    ON market_listing_observation(model_id, first_seen_at);

CREATE INDEX idx_market_listing_observation_model_last_seen
    ON market_listing_observation(model_id, last_seen_at);
