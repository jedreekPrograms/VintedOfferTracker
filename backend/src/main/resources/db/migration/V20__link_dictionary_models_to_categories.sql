ALTER TABLE dictionary_model
    ADD COLUMN category_id BIGINT;

ALTER TABLE dictionary_model
    ADD CONSTRAINT fk_dictionary_model_category
        FOREIGN KEY (category_id)
        REFERENCES dictionary_category(id);

CREATE INDEX idx_dictionary_model_category_id
    ON dictionary_model(category_id);
