-- PR #44 changes the meaning of a too-low first offer: an adaptive bot can now
-- raise that offer and continue within its global negotiation cap.
--
-- Listings skipped by the old absolute-price logic therefore deserve one new
-- evaluation. Static-price bots remain safe: Playwright will simply classify
-- them as SKIPPED_OFFER_TOO_LOW again if their configured price is still too low.
UPDATE listing
SET status = 'DISCOVERED'
WHERE status = 'SKIPPED_OFFER_TOO_LOW';
