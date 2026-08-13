CREATE TABLE bot_runtime_state (
    bot_id BIGINT PRIMARY KEY,
    runtime_status VARCHAR(32) NOT NULL,
    last_run_started_at TIMESTAMPTZ,
    last_run_finished_at TIMESTAMPTZ,
    next_run_at TIMESTAMPTZ,
    last_run_duration_ms BIGINT,
    consecutive_failures INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    worker_slot INTEGER,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_bot_runtime_state_bot
        FOREIGN KEY (bot_id)
        REFERENCES bot(id)
        ON DELETE CASCADE,
    CONSTRAINT chk_bot_runtime_state_status
        CHECK (runtime_status IN ('IDLE', 'QUEUED', 'WORKING', 'COOLDOWN', 'ERROR')),
    CONSTRAINT chk_bot_runtime_state_failures
        CHECK (consecutive_failures >= 0),
    CONSTRAINT chk_bot_runtime_state_duration
        CHECK (last_run_duration_ms IS NULL OR last_run_duration_ms >= 0)
);

CREATE INDEX idx_bot_runtime_state_status
    ON bot_runtime_state(runtime_status);

CREATE INDEX idx_bot_runtime_state_next_run_at
    ON bot_runtime_state(next_run_at);
