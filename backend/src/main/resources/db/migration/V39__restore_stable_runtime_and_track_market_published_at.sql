-- Preserve Flyway history from the pre-rollback branch while restoring the
-- stable runtime semantics: CAPTCHA_REQUIRED did not exist in the 2026-09-06
-- code and must not remain persisted when that enum is loaded.
UPDATE bot_runtime_state
SET runtime_status = 'ERROR'
WHERE runtime_status = 'CAPTCHA_REQUIRED';

ALTER TABLE bot_runtime_state
    DROP CONSTRAINT IF EXISTS chk_bot_runtime_state_status;

ALTER TABLE bot_runtime_state
    ADD CONSTRAINT chk_bot_runtime_state_status
        CHECK (runtime_status IN (
            'IDLE',
            'QUEUED',
            'WORKING',
            'COOLDOWN',
            'ERROR'
        ));

ALTER TABLE market_listing_observation
    ADD COLUMN IF NOT EXISTS published_at TIMESTAMP;

-- Existing observations predate publication-time enrichment. Keep their
-- previous first-seen semantics instead of rewriting historical statistics.
UPDATE market_listing_observation
SET published_at = first_seen_at
WHERE published_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_market_listing_observation_model_effective_published_at
    ON market_listing_observation (
        model_id,
        (COALESCE(published_at, first_seen_at))
    );
