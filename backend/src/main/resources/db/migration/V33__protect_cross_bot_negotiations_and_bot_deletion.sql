-- Cross-bot negotiation ownership.
--
-- Listing identity is intentionally scoped per bot (V29) so every bot keeps
-- an independent discovery/backlog history. Real negotiation ownership is a
-- different invariant: only one bot may ever start negotiating a given
-- marketplace listing. This durable tombstone survives bot/listing deletion.
CREATE TABLE marketplace_negotiation_claim
(
    id BIGSERIAL PRIMARY KEY,
    marketplace VARCHAR(64) NOT NULL,
    marketplace_listing_id VARCHAR(255) NOT NULL,
    owner_bot_id BIGINT NOT NULL,
    owner_listing_id BIGINT NOT NULL,
    request_id UUID,
    claimed_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_marketplace_negotiation_claim
        UNIQUE (marketplace, marketplace_listing_id)
);

CREATE INDEX idx_marketplace_negotiation_claim_owner
    ON marketplace_negotiation_claim (owner_bot_id, owner_listing_id);

-- Backfill listings for which a first offer is known to have happened, plus
-- unresolved FIRST_OFFER guards whose submit outcome may be ambiguous. If an
-- old database already contains the same marketplace item under multiple bots,
-- the oldest backend listing deterministically wins the historical claim.
INSERT INTO marketplace_negotiation_claim (
    marketplace,
    marketplace_listing_id,
    owner_bot_id,
    owner_listing_id,
    request_id,
    claimed_at
)
SELECT DISTINCT ON (bc.marketplace, l.listing_id)
       bc.marketplace,
       l.listing_id,
       l.bot_id,
       l.id,
       rag.request_id,
       COALESCE(rag.created_at, l.current_step_started_at, l.decision_at, CURRENT_TIMESTAMP)
FROM listing l
JOIN bot_configuration bc
  ON bc.bot_id = l.bot_id
LEFT JOIN real_action_guard rag
  ON rag.listing_id = l.id
 AND rag.action_type = 'FIRST_OFFER'
WHERE COALESCE(l.current_step, 0) >= 1
   OR l.conversation_id IS NOT NULL
   OR rag.id IS NOT NULL
ORDER BY bc.marketplace,
         l.listing_id,
         l.id ASC
ON CONFLICT (marketplace, marketplace_listing_id) DO NOTHING;

-- A bot that loses the global marketplace claim keeps its own listing row as a
-- terminal tombstone. It may still discover and negotiate other listings.
ALTER TABLE listing
DROP CONSTRAINT IF EXISTS listing_status_check;

ALTER TABLE listing
    ADD CONSTRAINT listing_status_check
        CHECK (
            status IN (
                'DISCOVERED',
                'NEGOTIATING',
                'ACTION_REQUIRED',
                'SKIPPED_OFFER_TOO_LOW',
                'SKIPPED_OUTSIDE_PRICE_RANGE',
                'SKIPPED_CANNOT_NEGOTIATE',
                'SKIPPED_TARGET_MISMATCH',
                'SKIPPED_ALREADY_NEGOTIATED',
                'SKIPPED_BY_USER',
                'UNAVAILABLE',
                'CONTACT_UNAVAILABLE',
                'REJECTED',
                'EXPIRED',
                'PURCHASED',
                'FINISHED'
            )
        );

-- Explicit bot deletion used to be able to fail because bot_command referenced
-- both bot and listing without ON DELETE CASCADE. Commands are operational
-- queue records and must disappear with their owning bot/listing.
ALTER TABLE bot_command
DROP CONSTRAINT IF EXISTS fk_bot_command_bot;

ALTER TABLE bot_command
    ADD CONSTRAINT fk_bot_command_bot
        FOREIGN KEY (bot_id)
            REFERENCES bot (id)
            ON DELETE CASCADE;

ALTER TABLE bot_command
DROP CONSTRAINT IF EXISTS fk_bot_command_listing;

ALTER TABLE bot_command
    ADD CONSTRAINT fk_bot_command_listing
        FOREIGN KEY (listing_id)
            REFERENCES listing (id)
            ON DELETE CASCADE;
