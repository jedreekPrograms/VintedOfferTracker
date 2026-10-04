ALTER TABLE bot_configuration
    ADD COLUMN IF NOT EXISTS negotiation_strategy_version INTEGER NOT NULL DEFAULT 1;

ALTER TABLE bot_additional_target
    ADD COLUMN IF NOT EXISTS negotiation_strategy_version INTEGER NOT NULL DEFAULT 1;

ALTER TABLE listing
    ADD COLUMN IF NOT EXISTS negotiation_strategy_version INTEGER,
    ADD COLUMN IF NOT EXISTS negotiation_strategy_snapshot TEXT;

CREATE INDEX IF NOT EXISTS idx_listing_negotiation_strategy_version
    ON listing (negotiation_strategy_version)
    WHERE negotiation_strategy_version IS NOT NULL;
