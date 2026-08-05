CREATE TABLE dictionary_category
(
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(255)  NOT NULL,
    path VARCHAR(1000) NOT NULL,

    CONSTRAINT uk_dictionary_category_path
        UNIQUE (path)
);

CREATE TABLE dictionary_brand
(
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,

    CONSTRAINT uk_dictionary_brand_name
        UNIQUE (name)
);

CREATE TABLE dictionary_model
(
    id       BIGSERIAL PRIMARY KEY,
    name     VARCHAR(255) NOT NULL,
    brand_id BIGINT       NOT NULL,

    CONSTRAINT fk_dictionary_model_brand
        FOREIGN KEY (brand_id)
            REFERENCES dictionary_brand (id),

    CONSTRAINT uk_dictionary_model_brand_name
        UNIQUE (brand_id, name)
);