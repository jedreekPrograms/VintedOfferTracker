ALTER TABLE market_model_scan_state
    ADD COLUMN IF NOT EXISTS publication_window_complete_at TIMESTAMP;
