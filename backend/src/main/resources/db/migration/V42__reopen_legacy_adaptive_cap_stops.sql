-- PR #263 changed the adaptive cap from a terminal stop into a price plateau:
-- once a calculated later step would exceed max_automatic_offer, the bot now
-- continues the configured ladder/messages at the cap price instead of ending
-- the conversation.
--
-- Conversations that hit the OLD behavior before that change are already
-- outside /listings/negotiating, so the new runtime cannot discover them by
-- itself. Re-open only rows that can be proven to have stopped exactly at that
-- old cap boundary.
--
-- Scope this migration to the MAIN product. Additional targets have their own
-- ladders and are intentionally left untouched rather than guessing which
-- configuration owned an old terminal row.

-- Formal seller rejection: REJECTED preserves the last own offer in
-- listing.current_price, so the old cap-stop can be reconstructed exactly.
WITH rejected_cap_stops AS (
    SELECT l.id
    FROM listing l
    JOIN bot_configuration bc
      ON bc.bot_id = l.bot_id
    JOIN negotiation_step current_step
      ON current_step.configuration_id = bc.id
     AND current_step.step_number = l.current_step
    JOIN LATERAL (
        SELECT ns.offer_price
        FROM negotiation_step ns
        WHERE ns.configuration_id = bc.id
          AND ns.step_number > l.current_step
        ORDER BY ns.step_number
        LIMIT 1
    ) next_step ON TRUE
    WHERE l.status = 'REJECTED'
      AND l.additional_target_id IS NULL
      AND l.conversation_id IS NOT NULL
      AND BTRIM(l.conversation_id) <> ''
      AND l.conversation_url IS NOT NULL
      AND BTRIM(l.conversation_url) <> ''
      AND l.current_step IS NOT NULL
      AND l.current_step > 0
      AND l.current_price IS NOT NULL
      AND l.current_price > 0
      AND bc.auto_raise_offer_to_vinted_minimum = TRUE
      AND bc.max_automatic_offer IS NOT NULL
      AND bc.max_automatic_offer > 0
      AND l.current_price <= bc.max_automatic_offer
      AND current_step.offer_price IS NOT NULL
      AND current_step.offer_price > 0
      AND next_step.offer_price IS NOT NULL
      AND next_step.offer_price > current_step.offer_price
      AND (
            CEIL(
                (
                    l.current_price
                    * next_step.offer_price
                    / current_step.offer_price
                ) / 10
            ) * 10
          ) > bc.max_automatic_offer
)
UPDATE listing l
SET status = 'NEGOTIATING',
    awaiting_seller_response = FALSE,
    decision_at = NULL
WHERE l.id IN (SELECT id FROM rejected_cap_stops);


-- Seller counteroffer: the old ACTION_REQUIRED transition stores the seller's
-- counter in listing.current_price, so restore the actual own offer from the
-- confirmed real-action audit before re-opening. A legitimate automatically
-- accepted counteroffer cannot be above max_automatic_offer, therefore the
-- l.current_price > cap predicate excludes normal ACTION_REQUIRED purchase
-- candidates. Final-step rows are excluded because a next configured step must
-- exist.
WITH action_required_cap_stops AS (
    SELECT
        l.id,
        last_action.offer_price AS own_offer_price
    FROM listing l
    JOIN bot_configuration bc
      ON bc.bot_id = l.bot_id
    JOIN negotiation_step current_step
      ON current_step.configuration_id = bc.id
     AND current_step.step_number = l.current_step
    JOIN LATERAL (
        SELECT ns.offer_price
        FROM negotiation_step ns
        WHERE ns.configuration_id = bc.id
          AND ns.step_number > l.current_step
        ORDER BY ns.step_number
        LIMIT 1
    ) next_step ON TRUE
    JOIN LATERAL (
        SELECT a.offer_price
        FROM real_action_audit a
        WHERE a.backend_listing_id = l.id
          AND a.step_number = l.current_step
          AND a.outcome = 'CONFIRMED'
          AND a.offer_price IS NOT NULL
          AND a.offer_price > 0
        ORDER BY a.updated_at DESC, a.id DESC
        LIMIT 1
    ) last_action ON TRUE
    WHERE l.status = 'ACTION_REQUIRED'
      AND l.additional_target_id IS NULL
      AND l.conversation_id IS NOT NULL
      AND BTRIM(l.conversation_id) <> ''
      AND l.conversation_url IS NOT NULL
      AND BTRIM(l.conversation_url) <> ''
      AND l.current_step IS NOT NULL
      AND l.current_step > 0
      AND l.current_price IS NOT NULL
      AND bc.auto_raise_offer_to_vinted_minimum = TRUE
      AND bc.max_automatic_offer IS NOT NULL
      AND bc.max_automatic_offer > 0
      -- ACTION_REQUIRED price is the seller counter. If it is above the cap,
      -- it was not an accepted-price action under the adaptive policy.
      AND l.current_price > bc.max_automatic_offer
      AND last_action.offer_price <= bc.max_automatic_offer
      AND current_step.offer_price IS NOT NULL
      AND current_step.offer_price > 0
      AND next_step.offer_price IS NOT NULL
      AND next_step.offer_price > current_step.offer_price
      AND (
            CEIL(
                (
                    last_action.offer_price
                    * next_step.offer_price
                    / current_step.offer_price
                ) / 10
            ) * 10
          ) > bc.max_automatic_offer
)
UPDATE listing l
SET status = 'NEGOTIATING',
    current_price = recovery.own_offer_price,
    awaiting_seller_response = FALSE,
    decision_at = NULL
FROM action_required_cap_stops recovery
WHERE l.id = recovery.id;
