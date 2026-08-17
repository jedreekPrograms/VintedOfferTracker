ALTER TABLE market_model_scan_state
    ADD COLUMN baseline_offer_count INTEGER;

UPDATE market_model_scan_state state
SET baseline_offer_count = counts.offer_count
FROM (
    SELECT model_id, COUNT(*)::INTEGER AS offer_count
    FROM market_listing_observation
    WHERE baseline = TRUE
    GROUP BY model_id
) counts
WHERE state.model_id = counts.model_id
  AND state.baseline_complete_at IS NOT NULL
  AND state.baseline_offer_count IS NULL;

UPDATE market_model_scan_state
SET baseline_offer_count = 0
WHERE baseline_complete_at IS NOT NULL
  AND baseline_offer_count IS NULL;

ALTER TABLE market_model_scan_state
    ADD CONSTRAINT ck_market_model_scan_state_baseline_offer_count_non_negative
    CHECK (baseline_offer_count IS NULL OR baseline_offer_count >= 0);
