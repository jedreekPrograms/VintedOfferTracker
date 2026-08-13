CREATE TABLE real_action_guard (
    id BIGSERIAL PRIMARY KEY,
    listing_id BIGINT NOT NULL,
    request_id UUID NOT NULL,
    action_type VARCHAR(32) NOT NULL,
    step_number INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_real_action_guard_listing
        FOREIGN KEY (listing_id)
        REFERENCES listing(id)
        ON DELETE CASCADE,
    CONSTRAINT uk_real_action_guard_listing
        UNIQUE (listing_id),
    CONSTRAINT uk_real_action_guard_request
        UNIQUE (request_id),
    CONSTRAINT ck_real_action_guard_action_type
        CHECK (action_type IN ('FIRST_OFFER', 'NEXT_STEP')),
    CONSTRAINT ck_real_action_guard_step_number
        CHECK (step_number > 0)
);

CREATE INDEX idx_real_action_guard_created_at
    ON real_action_guard(created_at);
