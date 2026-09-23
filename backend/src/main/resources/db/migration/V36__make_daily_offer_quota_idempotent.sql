CREATE TABLE IF NOT EXISTS daily_offer_quota_reservation (
    request_id UUID PRIMARY KEY,
    bot_id BIGINT NOT NULL REFERENCES bot(id) ON DELETE CASCADE,
    usage_date DATE NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    released_at TIMESTAMP NULL
);

CREATE INDEX IF NOT EXISTS idx_daily_offer_quota_reservation_bot_date_active
    ON daily_offer_quota_reservation (bot_id, usage_date, active);
