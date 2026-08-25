-- Separate a temporary pre-submit marketplace reservation from a durable
-- negotiation claim. A temporary reservation must never make another bot's
-- per-bot listing row terminal, because the owner may still fail before the
-- real submit click.
ALTER TABLE marketplace_negotiation_claim
    ADD COLUMN confirmed_at TIMESTAMP WITHOUT TIME ZONE;

-- Existing claims that are already proven by listing state are durable.
-- Claims whose owner listing has disappeared are also treated as durable:
-- their submit outcome can no longer be reconstructed safely, so failing
-- closed is the only safe migration behavior.
UPDATE marketplace_negotiation_claim AS claim
SET confirmed_at = claim.claimed_at
WHERE EXISTS (
          SELECT 1
          FROM listing AS owner_listing
          WHERE owner_listing.id = claim.owner_listing_id
            AND (
                COALESCE(owner_listing.current_step, 0) >= 1
                OR owner_listing.conversation_id IS NOT NULL
            )
      )
   OR NOT EXISTS (
          SELECT 1
          FROM listing AS owner_listing
          WHERE owner_listing.id = claim.owner_listing_id
      );

-- Unconfirmed claims are queried when validating safe bot deletion.
CREATE INDEX idx_marketplace_negotiation_claim_unconfirmed_owner
    ON marketplace_negotiation_claim (owner_bot_id)
    WHERE confirmed_at IS NULL;
