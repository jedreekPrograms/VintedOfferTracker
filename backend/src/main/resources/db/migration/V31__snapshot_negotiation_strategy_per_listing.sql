ALTER TABLE listing
    ADD COLUMN IF NOT EXISTS negotiation_strategy_snapshot TEXT;

COMMENT ON COLUMN listing.negotiation_strategy_snapshot IS
    'Immutable JSON snapshot of pricing, messages and response policies captured when the negotiation starts.';
