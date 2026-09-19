-- Re-open conversations that were previously closed only by local heuristics.
-- The Playwright runtime will inspect the real Vinted conversation again and
-- transition it only when it sees explicit terminal evidence.
UPDATE listing
SET status = 'NEGOTIATING',
    awaiting_seller_response = TRUE,
    decision_at = NULL
WHERE status IN ('EXPIRED', 'CONTACT_UNAVAILABLE')
  AND conversation_id IS NOT NULL
  AND BTRIM(conversation_id) <> ''
  AND conversation_url IS NOT NULL
  AND BTRIM(conversation_url) <> ''
  AND current_step IS NOT NULL
  AND current_step > 0;
