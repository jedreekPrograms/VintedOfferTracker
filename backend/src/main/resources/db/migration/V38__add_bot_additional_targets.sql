CREATE TABLE IF NOT EXISTS bot_additional_target (
    id BIGSERIAL PRIMARY KEY,
    configuration_id BIGINT NOT NULL,
    brand VARCHAR(255) NOT NULL,
    target_mode VARCHAR(40) NOT NULL,
    model VARCHAR(255),
    search_query VARCHAR(255),
    min_price NUMERIC(19, 2) NOT NULL,
    max_price NUMERIC(19, 2) NOT NULL,
    auto_raise_offer_to_vinted_minimum BOOLEAN NOT NULL DEFAULT FALSE,
    max_automatic_offer NUMERIC(19, 2),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_bot_additional_target_configuration
        FOREIGN KEY (configuration_id)
        REFERENCES bot_configuration(id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS bot_additional_target_category_path (
    target_id BIGINT NOT NULL,
    path_index INTEGER NOT NULL,
    category VARCHAR(255) NOT NULL,
    PRIMARY KEY (target_id, path_index),
    CONSTRAINT fk_bot_additional_target_category_path
        FOREIGN KEY (target_id)
        REFERENCES bot_additional_target(id)
        ON DELETE CASCADE
);

ALTER TABLE negotiation_step
    ADD COLUMN IF NOT EXISTS additional_target_id BIGINT;

-- Existing/main-product rows keep configuration_id populated. Additional-product
-- steps use additional_target_id instead, so the legacy parent column must be
-- nullable without changing any existing row.
ALTER TABLE negotiation_step
    ALTER COLUMN configuration_id DROP NOT NULL;

ALTER TABLE listing
    ADD COLUMN IF NOT EXISTS additional_target_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_negotiation_step_additional_target'
    ) THEN
        ALTER TABLE negotiation_step
            ADD CONSTRAINT fk_negotiation_step_additional_target
            FOREIGN KEY (additional_target_id)
            REFERENCES bot_additional_target(id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_listing_additional_target'
    ) THEN
        ALTER TABLE listing
            ADD CONSTRAINT fk_listing_additional_target
            FOREIGN KEY (additional_target_id)
            REFERENCES bot_additional_target(id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_bot_additional_target_configuration_active
    ON bot_additional_target (configuration_id, active, id);

CREATE INDEX IF NOT EXISTS idx_listing_bot_additional_target_status
    ON listing (bot_id, additional_target_id, status, id);
