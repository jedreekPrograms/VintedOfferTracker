ALTER TABLE listing
    ADD COLUMN IF NOT EXISTS history_outcome VARCHAR(40);

UPDATE listing
SET history_outcome = 'PURCHASED_BY_ME'
WHERE status = 'PURCHASED'
  AND history_outcome IS NULL;
