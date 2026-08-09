CREATE TABLE bot_command
(
    id BIGSERIAL PRIMARY KEY,

    bot_id BIGINT NOT NULL,

    listing_id BIGINT NOT NULL,

    type VARCHAR(50) NOT NULL,

    status VARCHAR(50) NOT NULL,

    error_message VARCHAR(1000),

    created_at TIMESTAMP WITH TIME ZONE NOT NULL,

    processed_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT fk_bot_command_bot
        FOREIGN KEY (bot_id)
            REFERENCES bot (id),

    CONSTRAINT fk_bot_command_listing
        FOREIGN KEY (listing_id)
            REFERENCES listing (id),

    CONSTRAINT bot_command_type_check
        CHECK (
            type IN (
                'OPEN_CONVERSATION'
                )
            ),

    CONSTRAINT bot_command_status_check
        CHECK (
            status IN (
                       'PENDING',
                       'PROCESSING',
                       'COMPLETED',
                       'FAILED'
                )
            )
);

CREATE INDEX idx_bot_command_bot_status
    ON bot_command (bot_id, status, id);