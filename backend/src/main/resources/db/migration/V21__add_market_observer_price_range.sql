ALTER TABLE dictionary_model
    ADD COLUMN market_min_price NUMERIC(38, 2),
    ADD COLUMN market_max_price NUMERIC(38, 2);

ALTER TABLE dictionary_model
    ADD CONSTRAINT chk_dictionary_model_market_min_price_positive
        CHECK (market_min_price IS NULL OR market_min_price > 0),
    ADD CONSTRAINT chk_dictionary_model_market_max_price_positive
        CHECK (market_max_price IS NULL OR market_max_price > 0),
    ADD CONSTRAINT chk_dictionary_model_market_price_range
        CHECK (
            market_min_price IS NULL
            OR market_max_price IS NULL
            OR market_min_price <= market_max_price
        );
