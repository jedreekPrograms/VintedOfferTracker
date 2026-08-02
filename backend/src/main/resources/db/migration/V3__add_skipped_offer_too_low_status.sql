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
                       'REJECTED',
                       'FINISHED'
                )
            );