ALTER TABLE listing
    ADD COLUMN IF NOT EXISTS buy_candidate_at TIMESTAMP;

-- PURCHASED and SKIPPED_BY_USER can only be reached from ACTION_REQUIRED in
-- the existing backend. They are therefore safe legacy evidence that the row
-- really appeared in "Oferty do kupienia".
UPDATE listing
SET buy_candidate_at = COALESCE(
        decision_at,
        current_step_started_at,
        last_fresh_discovery_at,
        CASE
            WHEN status = 'ACTION_REQUIRED' THEN CURRENT_TIMESTAMP
            ELSE TIMESTAMP '1970-01-01 00:00:00'
        END
    )
WHERE buy_candidate_at IS NULL
  AND status IN (
      'ACTION_REQUIRED',
      'PURCHASED',
      'SKIPPED_BY_USER'
  );

-- "Utracona okazja" is represented as a reason under the simpler
-- "Nie kupiłem" outcome.
UPDATE listing
SET history_outcome = 'REJECTED'
WHERE history_outcome = 'MISSED_OPPORTUNITY';

CREATE INDEX IF NOT EXISTS idx_listing_buy_candidate_history
    ON listing (buy_candidate_at, status)
    WHERE buy_candidate_at IS NOT NULL;