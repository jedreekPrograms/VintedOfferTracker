ALTER TABLE listing
    ADD COLUMN IF NOT EXISTS product_target_label VARCHAR(1000);
