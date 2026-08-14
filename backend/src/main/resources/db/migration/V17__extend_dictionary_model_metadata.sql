ALTER TABLE dictionary_model
    ADD COLUMN target_mode VARCHAR(32) NOT NULL DEFAULT 'VINTED_MODEL',
    ADD COLUMN proposed_offer_price NUMERIC(38, 2),
    ADD COLUMN expected_resale_price NUMERIC(38, 2);

ALTER TABLE dictionary_model
    ADD CONSTRAINT ck_dictionary_model_target_mode
        CHECK (target_mode IN ('VINTED_MODEL', 'SEARCH_QUERY')),
    ADD CONSTRAINT ck_dictionary_model_proposed_offer_price
        CHECK (proposed_offer_price IS NULL OR proposed_offer_price > 0),
    ADD CONSTRAINT ck_dictionary_model_expected_resale_price
        CHECK (expected_resale_price IS NULL OR expected_resale_price > 0);
