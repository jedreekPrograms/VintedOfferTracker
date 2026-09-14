-- Additional-product targets are retained while a bot exists (deactivation is
-- soft), but deleting the bot deletes its BotConfiguration and therefore its
-- additional targets. The target-owned negotiation steps/listings must not
-- block that configuration cascade.

ALTER TABLE negotiation_step
    DROP CONSTRAINT IF EXISTS fk_negotiation_step_additional_target;

ALTER TABLE negotiation_step
    ADD CONSTRAINT fk_negotiation_step_additional_target
    FOREIGN KEY (additional_target_id)
    REFERENCES bot_additional_target(id)
    ON DELETE CASCADE;

ALTER TABLE listing
    DROP CONSTRAINT IF EXISTS fk_listing_additional_target;

ALTER TABLE listing
    ADD CONSTRAINT fk_listing_additional_target
    FOREIGN KEY (additional_target_id)
    REFERENCES bot_additional_target(id)
    ON DELETE CASCADE;
