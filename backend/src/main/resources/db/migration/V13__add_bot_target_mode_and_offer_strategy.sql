ALTER TABLE bot_configuration
    ADD COLUMN target_mode VARCHAR(255) NOT NULL DEFAULT 'VINTED_MODEL';

ALTER TABLE bot_configuration
    ADD COLUMN search_query VARCHAR(255);

ALTER TABLE bot_configuration
    ADD COLUMN auto_raise_offer_to_vinted_minimum BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE bot_configuration
    ADD COLUMN max_automatic_offer NUMERIC(38, 2);

ALTER TABLE bot_configuration
    ADD CONSTRAINT bot_configuration_target_mode_check
        CHECK (
            target_mode IN (
                'VINTED_MODEL',
                'SEARCH_QUERY'
            )
        );

UPDATE bot_configuration
SET target_mode = 'VINTED_MODEL'
WHERE target_mode IS NULL;

UPDATE bot_configuration
SET auto_raise_offer_to_vinted_minimum = FALSE
WHERE auto_raise_offer_to_vinted_minimum IS NULL;
