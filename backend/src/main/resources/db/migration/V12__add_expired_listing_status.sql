ALTER TABLE listing
DROP CONSTRAINT IF EXISTS listing_status_check;

ALTER TABLE listing
    ADD CONSTRAINT listing_status_check
        CHECK (
            status IN (
                       'DISCOVERED',
                       'NEGOTIATING',
                       'ACTION_REQUIRED',
                       'SKIPPED_OFFER_TOO_LOW',
                       'SKIPPED_OUTSIDE_PRICE_RANGE',
                       'SKIPPED_BY_USER',
                       'UNAVAILABLE',
                       'REJECTED',
                       'EXPIRED',
                       'PURCHASED',
                       'FINISHED'
                )
            );

-- Czyścimy aktywność, która jest starsza niż aktualny krok negocjacji.
-- Taki rekord nie może uruchomić timera 3h dla późniejszej oferty.
UPDATE listing
SET seller_activity_at = NULL
WHERE seller_activity_at IS NOT NULL
  AND current_step_started_at IS NOT NULL
  AND seller_activity_at < current_step_started_at;

UPDATE listing
SET read_detected_at = NULL
WHERE read_detected_at IS NOT NULL
  AND current_step_started_at IS NOT NULL
  AND read_detected_at < current_step_started_at;