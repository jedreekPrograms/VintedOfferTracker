CREATE TABLE IF NOT EXISTS price_probe (
    id BIGSERIAL PRIMARY KEY,
    probe_bot_id BIGINT NOT NULL,
    source_listing_id BIGINT NOT NULL,
    reference_offer_price NUMERIC(19, 2) NOT NULL,
    probe_price NUMERIC(19, 2) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    status VARCHAR(32) NOT NULL,
    claimed_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP NULL,
    failure_reason VARCHAR(1000) NULL,
    CONSTRAINT fk_price_probe_bot
        FOREIGN KEY (probe_bot_id) REFERENCES bot(id),
    CONSTRAINT fk_price_probe_source_listing
        FOREIGN KEY (source_listing_id) REFERENCES listing(id),
    CONSTRAINT uk_price_probe_bot_listing
        UNIQUE (probe_bot_id, source_listing_id),
    CONSTRAINT chk_price_probe_status
        CHECK (status IN ('CLAIMED', 'SENT', 'FAILED', 'UNKNOWN', 'CANCELLED'))
);

CREATE INDEX IF NOT EXISTS idx_price_probe_source_listing
    ON price_probe(source_listing_id);

CREATE INDEX IF NOT EXISTS idx_price_probe_bot_status
    ON price_probe(probe_bot_id, status);
