-- Per-step strategy for formal rejection and seller counteroffers.
--
-- Existing bots are migrated to the requested useful defaults:
--   rejection of step 1 -> next step immediately
--   rejection of step 2 -> wait 6h
--   rejection of step 3 -> wait 12h
--   rejection of step 4+ -> wait 24h
--
-- Seller counteroffer defaults for every existing step:
--   below the first configured discount threshold -> wait 6h
--   >= 10% below ORIGINAL listing price -> wait 2h
--   >= 15% below ORIGINAL listing price -> next step immediately
--
-- The UI can later replace/remove/add these rules independently per step.

ALTER TABLE negotiation_step
    ADD COLUMN IF NOT EXISTS rejection_action varchar(40),
    ADD COLUMN IF NOT EXISTS rejection_wait_hours integer,
    ADD COLUMN IF NOT EXISTS counter_offer_default_action varchar(40),
    ADD COLUMN IF NOT EXISTS counter_offer_default_wait_hours integer;

UPDATE negotiation_step
SET rejection_action = CASE
        WHEN step_number = 1 THEN 'NEXT_STEP_NOW'
        ELSE 'WAIT_BEFORE_NEXT_STEP'
    END,
    rejection_wait_hours = CASE
        WHEN step_number = 1 THEN NULL
        WHEN step_number = 2 THEN 6
        WHEN step_number = 3 THEN 12
        ELSE 24
    END,
    counter_offer_default_action = 'WAIT_BEFORE_NEXT_STEP',
    counter_offer_default_wait_hours = 6
WHERE rejection_action IS NULL
   OR counter_offer_default_action IS NULL;

ALTER TABLE negotiation_step
    ALTER COLUMN rejection_action SET NOT NULL,
    ALTER COLUMN counter_offer_default_action SET NOT NULL;

CREATE TABLE IF NOT EXISTS negotiation_step_counter_offer_rule (
    negotiation_step_id bigint NOT NULL,
    rule_index integer NOT NULL,
    minimum_discount_percent numeric(7,3) NOT NULL,
    reaction_action varchar(40) NOT NULL,
    wait_hours integer,
    CONSTRAINT fk_negotiation_step_counter_offer_rule_step
        FOREIGN KEY (negotiation_step_id)
        REFERENCES negotiation_step(id)
        ON DELETE CASCADE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_negotiation_step_counter_rule_position
    ON negotiation_step_counter_offer_rule(negotiation_step_id, rule_index);

INSERT INTO negotiation_step_counter_offer_rule (
    negotiation_step_id,
    rule_index,
    minimum_discount_percent,
    reaction_action,
    wait_hours
)
SELECT
    ns.id,
    defaults.rule_index,
    defaults.minimum_discount_percent,
    defaults.reaction_action,
    defaults.wait_hours
FROM negotiation_step ns
CROSS JOIN (
    VALUES
        (0, 10.000::numeric, 'WAIT_BEFORE_NEXT_STEP'::varchar, 2),
        (1, 15.000::numeric, 'NEXT_STEP_NOW'::varchar, NULL::integer)
) AS defaults(
    rule_index,
    minimum_discount_percent,
    reaction_action,
    wait_hours
)
WHERE NOT EXISTS (
    SELECT 1
    FROM negotiation_step_counter_offer_rule existing
    WHERE existing.negotiation_step_id = ns.id
);

ALTER TABLE listing
    ADD COLUMN IF NOT EXISTS formal_response_fingerprint varchar(255),
    ADD COLUMN IF NOT EXISTS formal_response_detected_at timestamp;
