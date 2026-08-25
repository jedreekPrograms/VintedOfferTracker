ALTER TABLE negotiation_step
    ADD COLUMN read_wait_hours INTEGER NOT NULL DEFAULT 3,
    ADD COLUMN unread_wait_hours INTEGER NOT NULL DEFAULT 48;

ALTER TABLE negotiation_step
    ADD CONSTRAINT negotiation_step_read_wait_hours_check
        CHECK (read_wait_hours BETWEEN 1 AND 720),
    ADD CONSTRAINT negotiation_step_unread_wait_hours_check
        CHECK (unread_wait_hours BETWEEN 1 AND 720);
