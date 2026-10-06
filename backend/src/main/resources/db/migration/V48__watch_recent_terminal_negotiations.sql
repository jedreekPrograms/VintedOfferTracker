ALTER TABLE listing
    ADD COLUMN IF NOT EXISTS last_terminal_watch_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_listing_terminal_watch
    ON listing (status, last_terminal_watch_at, decision_at);
