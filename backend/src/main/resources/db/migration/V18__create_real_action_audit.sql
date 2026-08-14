CREATE TABLE real_action_audit (
    id BIGSERIAL PRIMARY KEY,
    request_id UUID NOT NULL,
    bot_id BIGINT NOT NULL,
    backend_listing_id BIGINT NOT NULL,
    marketplace_listing_id VARCHAR(255) NOT NULL,
    conversation_id VARCHAR(255),
    action_type VARCHAR(32) NOT NULL,
    step_number INTEGER NOT NULL,
    offer_price NUMERIC(38, 2) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    message_status VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
    failure_reason VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_real_action_audit_request UNIQUE (request_id),
    CONSTRAINT ck_real_action_audit_action_type
        CHECK (action_type IN ('FIRST_OFFER', 'NEXT_STEP')),
    CONSTRAINT ck_real_action_audit_step_number
        CHECK (step_number > 0),
    CONSTRAINT ck_real_action_audit_offer_price
        CHECK (offer_price > 0),
    CONSTRAINT ck_real_action_audit_outcome
        CHECK (outcome IN ('CONFIRMED', 'AMBIGUOUS')),
    CONSTRAINT ck_real_action_audit_message_status
        CHECK (message_status IN ('CONFIRMED', 'FAILED', 'UNKNOWN'))
);

CREATE INDEX idx_real_action_audit_bot_created
    ON real_action_audit(bot_id, created_at DESC);

CREATE INDEX idx_real_action_audit_marketplace_listing
    ON real_action_audit(marketplace_listing_id);

CREATE INDEX idx_real_action_audit_backend_listing
    ON real_action_audit(backend_listing_id);
