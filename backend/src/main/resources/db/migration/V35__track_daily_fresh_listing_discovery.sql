ALTER TABLE listing
    ADD COLUMN last_fresh_discovery_at TIMESTAMP;

-- Existing DISCOVERED rows are already in the live candidate pool. Mark them as
-- seen today so that, if one of them becomes transiently ineligible later on
-- the same day, it is not immediately recycled again by the next catalog scan.
-- Historical terminal rows intentionally remain NULL: the first future fresh
-- scan that actually sees them again may give them one controlled retry.
UPDATE listing
SET last_fresh_discovery_at = CURRENT_TIMESTAMP AT TIME ZONE 'Europe/Warsaw'
WHERE status = 'DISCOVERED'
  AND last_fresh_discovery_at IS NULL;
