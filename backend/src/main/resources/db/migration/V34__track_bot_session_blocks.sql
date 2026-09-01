ALTER TABLE bot_runtime_state
    ADD COLUMN session_blocked_since TIMESTAMPTZ,
    ADD COLUMN session_block_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE bot_runtime_state
    ADD CONSTRAINT chk_bot_runtime_state_session_block_count
        CHECK (session_block_count >= 0);
