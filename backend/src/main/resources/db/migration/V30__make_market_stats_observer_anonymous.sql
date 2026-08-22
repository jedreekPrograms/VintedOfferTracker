-- Market statistics collection uses only the public Vinted catalog and no
-- longer needs a Vinted account.
--
-- Preserve the technical observer row/id, but remove every account credential
-- from it. This immediately releases the previously configured Vinted e-mail
-- so it can be reused by a normal negotiation bot. Playwright also recognizes
-- this credential-free technical observer and deliberately ignores any old
-- sessions/bot-<id>.json file that may still exist locally.

UPDATE bot
SET name = 'Anonymous Market Observer',
    email = NULL,
    password = NULL,
    status = 'STOPPED'
WHERE market_stats_observer = TRUE;

-- Fresh installations or databases that never configured the old observer UI
-- still need one internal identity for the existing market-stats API contract.
INSERT INTO bot (
    name,
    email,
    password,
    status,
    market_stats_observer
)
SELECT
    'Anonymous Market Observer',
    NULL,
    NULL,
    'STOPPED',
    TRUE
WHERE NOT EXISTS (
    SELECT 1
    FROM bot
    WHERE market_stats_observer = TRUE
);
