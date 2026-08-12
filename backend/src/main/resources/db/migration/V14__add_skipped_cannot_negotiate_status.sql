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
                       'SKIPPED_CANNOT_NEGOTIATE',
                       'SKIPPED_BY_USER',
                       'UNAVAILABLE',
                       'REJECTED',
                       'EXPIRED',
                       'PURCHASED',
                       'FINISHED'
                )
            );