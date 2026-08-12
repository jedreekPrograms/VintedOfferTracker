ALTER TABLE listing
    ADD COLUMN current_step_started_at TIMESTAMP,
    ADD COLUMN seller_activity_at TIMESTAMP,
    ADD COLUMN read_detected_at TIMESTAMP;

UPDATE listing
SET current_step_started_at = CURRENT_TIMESTAMP
WHERE status = 'NEGOTIATING'
  AND current_step_started_at IS NULL;