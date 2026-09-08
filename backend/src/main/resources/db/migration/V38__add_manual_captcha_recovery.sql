ALTER TABLE bot_runtime_state
    ADD COLUMN captcha_required_since TIMESTAMPTZ,
    ADD COLUMN captcha_recovery_requested_at TIMESTAMPTZ,
    ADD COLUMN captcha_challenge_url TEXT;

ALTER TABLE bot_runtime_state
    DROP CONSTRAINT chk_bot_runtime_state_status;

ALTER TABLE bot_runtime_state
    ADD CONSTRAINT chk_bot_runtime_state_status
        CHECK (runtime_status IN (
            'IDLE',
            'QUEUED',
            'WORKING',
            'COOLDOWN',
            'CAPTCHA_REQUIRED',
            'ERROR'
        ));
