-- Global marketplace negotiation ownership.
--
-- Listing discovery intentionally remains scoped per bot, so different bots may
-- discover/evaluate the same marketplace listing. Real negotiation ownership is
-- global: only one bot may negotiate a given (marketplace, listing id).
CREATE TABLE marketplace_negotiation_claim
(
    id BIGSERIAL PRIMARY KEY,
    marketplace VARCHAR(64) NOT NULL,
    marketplace_listing_id VARCHAR(255) NOT NULL,
    owner_bot_id BIGINT NOT NULL,
    owner_listing_id BIGINT NOT NULL,
    request_id UUID,
    claimed_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_at TIMESTAMP WITHOUT TIME ZONE,

    CONSTRAINT uk_marketplace_negotiation_claim
        UNIQUE (marketplace, marketplace_listing_id)
);

CREATE INDEX idx_marketplace_negotiation_claim_owner
    ON marketplace_negotiation_claim (owner_bot_id, owner_listing_id);

CREATE INDEX idx_marketplace_negotiation_claim_unconfirmed_owner
    ON marketplace_negotiation_claim (owner_bot_id)
    WHERE confirmed_at IS NULL;

-- Backfill every negotiation for which the first real offer is already proven,
-- plus unresolved FIRST_OFFER guards whose outcome may be ambiguous. If the old
-- database already contains the same marketplace listing under several bots,
-- the oldest backend listing deterministically becomes the durable owner.
INSERT INTO marketplace_negotiation_claim (
    marketplace,
    marketplace_listing_id,
    owner_bot_id,
    owner_listing_id,
    request_id,
    claimed_at,
    confirmed_at
)
SELECT DISTINCT ON (bc.marketplace, l.listing_id)
       bc.marketplace,
       l.listing_id,
       l.bot_id,
       l.id,
       rag.request_id,
       COALESCE(
           rag.created_at,
           l.current_step_started_at,
           l.decision_at,
           CURRENT_TIMESTAMP
       ),
       COALESCE(
           rag.created_at,
           l.current_step_started_at,
           l.decision_at,
           CURRENT_TIMESTAMP
       )
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

-- Add a terminal state for a per-bot row that lost global negotiation ownership.
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

-- Repair already-existing duplicate negotiations. The claim backfill above chose
-- one deterministic owner. Any other currently active per-bot copy becomes
-- terminal immediately so two bots cannot continue negotiating the same offer.
UPDATE listing AS losing_listing
SET status = 'SKIPPED_ALREADY_NEGOTIATED',
    decision_at = COALESCE(losing_listing.decision_at, CURRENT_TIMESTAMP)
FROM bot_configuration AS losing_configuration,
     marketplace_negotiation_claim AS claim
WHERE losing_configuration.bot_id = losing_listing.bot_id
  AND losing_configuration.marketplace = claim.marketplace
  AND losing_listing.listing_id = claim.marketplace_listing_id
  AND losing_listing.id <> claim.owner_listing_id
  AND losing_listing.status IN ('NEGOTIATING', 'ACTION_REQUIRED');
