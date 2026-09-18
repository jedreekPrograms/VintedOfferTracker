-- Hot listing reads are dominated by lifecycle status inside one bot,
-- global status dashboards/action-required reads, and visible history.
-- The existing unique (bot_id, listing_id) index cannot efficiently satisfy
-- bot_id + status scans, and there was no global status index.
--
-- Keep the index set deliberately small because listing is also write-heavy
-- during discovery and negotiation updates.

CREATE INDEX IF NOT EXISTS idx_listing_bot_status_id
    ON listing (bot_id, status, id);

CREATE INDEX IF NOT EXISTS idx_listing_status_id
    ON listing (status, id);

CREATE INDEX IF NOT EXISTS idx_listing_visible_history_status_decision
    ON listing (status, decision_at DESC, id)
    WHERE history_hidden = FALSE;
