-- Re-evaluate listings that were permanently skipped after a single
-- 15-second missing-offer-button observation. Playwright now confirms the
-- absence across three fully loaded checks with reloads before returning
-- CANNOT_NEGOTIATE, so old decisions need one safe retry under the stronger
-- detector.
UPDATE listing
SET status = 'DISCOVERED'
WHERE status = 'SKIPPED_CANNOT_NEGOTIATE'
  AND current_step = 0;
