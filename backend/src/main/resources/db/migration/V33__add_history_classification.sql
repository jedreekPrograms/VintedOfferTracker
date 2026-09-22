ALTER TABLE listing
    ADD COLUMN history_outcome VARCHAR(40),
    ADD COLUMN offer_assessment VARCHAR(40) NOT NULL DEFAULT 'UNASSESSED',
    ADD COLUMN missed_opportunity_reason VARCHAR(40);

UPDATE listing
SET history_outcome = 'PURCHASED'
WHERE status = 'PURCHASED'
  AND history_outcome IS NULL;

UPDATE listing
SET history_outcome = 'REJECTED'
WHERE status = 'SKIPPED_BY_USER'
  AND history_outcome IS NULL;

-- Older terminal rows did not consistently receive decision_at. The last
-- negotiation-step timestamp is the closest persisted event timestamp and
-- keeps old history usable in period filters without changing bot behaviour.
UPDATE listing
SET decision_at = COALESCE(current_step_started_at, last_fresh_discovery_at)
WHERE decision_at IS NULL
  AND status IN (
      'UNAVAILABLE',
      'CONTACT_UNAVAILABLE',
      'REJECTED',
      'EXPIRED',
      'FINISHED'
  );

-- Backfill the immutable product label for old rows when the original target
-- still exists. New rows already snapshot this at claim time.
UPDATE listing l
SET product_target_label =
        trim(t.brand)
        || ' → '
        || trim(
            CASE
                WHEN t.target_mode = 'SEARCH_QUERY' THEN t.search_query
                ELSE t.model
            END
        )
FROM bot_additional_target t
WHERE l.product_target_label IS NULL
  AND l.additional_target_id = t.id
  AND t.brand IS NOT NULL
  AND CASE
          WHEN t.target_mode = 'SEARCH_QUERY' THEN t.search_query
          ELSE t.model
      END IS NOT NULL;

UPDATE listing l
SET product_target_label =
        trim(c.brand)
        || ' → '
        || trim(
            CASE
                WHEN c.target_mode = 'SEARCH_QUERY' THEN c.search_query
                ELSE c.model
            END
        )
FROM bot_configuration c
WHERE l.product_target_label IS NULL
  AND l.additional_target_id IS NULL
  AND l.bot_id = c.bot_id
  AND c.brand IS NOT NULL
  AND CASE
          WHEN c.target_mode = 'SEARCH_QUERY' THEN c.search_query
          ELSE c.model
      END IS NOT NULL;

CREATE INDEX idx_listing_history_classification
    ON listing(history_outcome, offer_assessment);
