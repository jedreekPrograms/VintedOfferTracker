ALTER TABLE listing
    ADD COLUMN history_outcome VARCHAR(40),
    ADD COLUMN offer_assessment VARCHAR(40) NOT NULL DEFAULT 'UNASSESSED',
    ADD COLUMN missed_opportunity_reason VARCHAR(40);

-- Normalize values from earlier experimental history implementations before
-- JPA tries to hydrate the enum-backed columns.
UPDATE listing
SET history_outcome = 'PURCHASED'
WHERE upper(trim(history_outcome)) IN (
    'PURCHASED_BY_ME',
    'BOUGHT_BY_ME',
    'BOUGHT'
);

UPDATE listing
SET history_outcome = 'REJECTED'
WHERE upper(trim(history_outcome)) IN (
    'REJECTED_BY_ME',
    'SKIPPED_BY_ME'
);

UPDATE listing
SET history_outcome = CASE
    WHEN status = 'PURCHASED' THEN 'PURCHASED'
    WHEN status = 'SKIPPED_BY_USER' THEN 'REJECTED'
    ELSE 'UNCLASSIFIED'
END
WHERE history_outcome IS NOT NULL
  AND upper(trim(history_outcome)) NOT IN (
      'UNCLASSIFIED',
      'PURCHASED',
      'REJECTED',
      'MISSED_OPPORTUNITY'
  );

UPDATE listing
SET offer_assessment = 'UNASSESSED'
WHERE offer_assessment IS NULL
   OR upper(trim(offer_assessment)) NOT IN (
       'UNASSESSED',
       'LEGIT',
       'SCAM'
   );

UPDATE listing
SET missed_opportunity_reason = NULL
WHERE missed_opportunity_reason IS NOT NULL
  AND upper(trim(missed_opportunity_reason)) NOT IN (
      'SOLD_BEFORE_PURCHASE',
      'NO_FUNDS',
      'TOO_SLOW',
      'OTHER'
  );

ALTER TABLE listing
    ALTER COLUMN offer_assessment SET DEFAULT 'UNASSESSED',
    ALTER COLUMN offer_assessment SET NOT NULL;

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
      'EXPIRED'
  );

-- Backfill the immutable product label for old rows only when the current
-- linked target and the stored listing title independently agree on the target.
-- A bot may have been repurposed since an old listing was handled, so unresolved
-- legacy rows deliberately stay unassigned rather than contaminating model stats.
-- New rows already snapshot this at claim time.
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
      END IS NOT NULL
  AND position(
          lower(
              trim(
                  CASE
                      WHEN t.target_mode = 'SEARCH_QUERY' THEN t.search_query
                      ELSE t.model
                  END
              )
          )
          in lower(l.title)
      ) > 0;

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
      END IS NOT NULL
  AND position(
          lower(
              trim(
                  CASE
                      WHEN c.target_mode = 'SEARCH_QUERY' THEN c.search_query
                      ELSE c.model
                  END
              )
          )
          in lower(l.title)
      ) > 0;

CREATE INDEX idx_listing_history_classification
    ON listing(history_outcome, offer_assessment);