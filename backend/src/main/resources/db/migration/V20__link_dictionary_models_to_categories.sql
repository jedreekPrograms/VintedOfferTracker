ALTER TABLE dictionary_model
    ADD COLUMN category_id BIGINT;

ALTER TABLE dictionary_model
    ADD CONSTRAINT fk_dictionary_model_category
        FOREIGN KEY (category_id)
        REFERENCES dictionary_category(id);

CREATE INDEX idx_dictionary_model_category_id
    ON dictionary_model(category_id);

-- Market statistics collected before V20 used text search for every model,
-- including models configured as VINTED_MODEL. Start a clean baseline after
-- dictionary-driven category/brand/model filtering is introduced.
DELETE FROM market_listing_observation;
DELETE FROM market_model_scan_state;
