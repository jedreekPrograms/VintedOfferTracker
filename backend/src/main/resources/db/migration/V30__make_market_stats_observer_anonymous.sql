-- Market statistics collection uses only the public Vinted catalog and no
-- longer needs a Vinted account. Replace the old credential-bearing observer
-- bot with a fresh technical identity so:
--   1) the previously configured Vinted e-mail becomes immediately reusable
--      by a normal negotiation bot,
--   2) the new observer receives a new bot id and therefore cannot restore the
--      old account's sessions/bot-<id>.json storage state,
--   3) no Vinted credentials are stored for the observer going forward.
--
-- Market-stat history lives in market_model_scan_state and
-- market_listing_observation, independently of the observer bot row.

DELETE FROM bot
WHERE market_stats_observer = TRUE;

INSERT INTO bot (
    name,
    email,
    password,
    status,
    market_stats_observer
)
VALUES (
    'Anonymous Market Observer',
    NULL,
    NULL,
    'STOPPED',
    TRUE
);
