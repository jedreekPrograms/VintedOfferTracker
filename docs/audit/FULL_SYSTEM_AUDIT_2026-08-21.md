# Full System Integrity Audit — 2026-08-21

This document records the invariants verified during the full FlipBot audit and the fixes applied by the audit branch.

## Core invariants

- Marketplace discovery is persisted per bot, so each bot owns an independent DISCOVERED backlog.
- A marketplace listing may be discovered by multiple bots, but only one bot may hold the right to start a real negotiation for the same marketplace/listing identity at a time.
- A real FIRST_OFFER is guarded before quota reservation and before the submit action.
- A cross-bot marketplace claim is durable after a confirmed or potentially-real submit, but a claim that is proven to have been released before submit must not permanently poison competing bots' rows.
- Competing rows marked `SKIPPED_ALREADY_NEGOTIATED` only because of a temporary pre-submit claim are reopened as `DISCOVERED` when that exact claim is safely released. Reopening uses PostgreSQL `FOR UPDATE ... SKIP LOCKED` so claim release does not deadlock with a competitor already racing for the same item.
- Ambiguous post-submit failures fail closed: quota/guard/marketplace claim are not silently released.
- Daily quota counts real actions, while negotiation capacity separately reserves future automated steps for NEGOTIATING conversations.
- ACTION_REQUIRED is manual and therefore does not reserve future automated negotiation steps.
- Sold/unavailable/terminal listings remain persisted; routine runtime logic never deletes them merely because they became terminal.
- VINTED_MODEL trusts the exact persisted Vinted collection filter and does not reinterpret seller-written titles.
- SEARCH_QUERY keeps semantic product-family, generation and variant verification.
- Read/no-read follow-up delays belong to the current negotiation step and are persisted/configurable independently.
- When a read/no-read timer elapses, the runtime advances to the next configured step when one exists; only an exhausted/capped ladder becomes EXPIRED.
- Controlled real-action mode remains one action. Fully armed production throughput is governed by backend planner/quota/action guards unless an operator explicitly configures a lower runtime throttle.
- Deleting a bot must not be blocked by historical bot_command foreign keys.

## SEARCH_QUERY Samsung identity rules

SEARCH_QUERY matching is structural rather than a collection of one-off string exceptions. Seller-written titles are normalized into model/family/variant tokens and then checked against the configured target.

Supported forms include, among others:

- S-series: `Galaxy S25+`, `S25 Plus`, `GalaxyS25+`, `S25Plus`.
- Tab: `Galaxy Tab S10+`, `Tab S10 Plus`, `TabS10Plus`, `GalaxyTabS11Ultra`.
- Fold/Flip: `Galaxy Z Fold 7`, `ZFold7`, `Fold7`, `Galaxy Fold 7`, plus the equivalent Flip forms.
- Note/XCover: spaced and compact generation spelling such as `Note20 Ultra`, `Note 20 Ultra`, `XCover7`, `XCover 7`.
- A/M families: normal compact generation names such as `A56` and `M55`.
- Multi-variant names such as `Tab S9 FE+`, `TabS9FE Plus` and `GalaxyTabS9FE+`.
- Samsung technical product codes such as `SM-S931` do not override the marketing model identity.

The matcher deliberately keeps semantic boundaries strict:

- generation mismatches are rejected (`S25` vs `S24`, `Fold7` vs `Fold6`);
- unexpected variants are rejected (`S25` vs `S25 Ultra`, `S25 Edge`, `S25 FE`, `S25+`);
- requested variants must actually be present (`S25+` does not match plain `S25`);
- family names such as `Tab`, `Fold`, `Flip`, `Note` and `XCover` are not optional, preventing e.g. phone `Galaxy S10+` from matching tablet `Galaxy Tab S10+`;
- `Galaxy` and the marketing `Z` token may be omitted for Samsung seller titles when the remaining family/generation identity is still exact.

This logic applies only to SEARCH_QUERY. VINTED_MODEL remains intentionally different: once FlipBot has proven the exact visible Vinted model row and exact `brand_collection_ids[]` value, Vinted's classification is the source of truth and the seller title is not reinterpreted.

The exact-model UI parser accepts only the verified Vinted test-id shapes `selectable-item-brand_collection-<numeric-id>` and `selectable-item-brand_collection-<numeric-id>--title`. Arbitrary suffixes and nonnumeric IDs are rejected, so DOM compatibility does not weaken exact collection-ID proof.

## Audit fixes

1. Durable cross-bot marketplace negotiation claims and terminal `SKIPPED_ALREADY_NEGOTIATED` loser state for genuinely owned/ambiguous negotiations.
2. Safe recovery of temporary cross-bot losers when the exact owner claim is released before submit, with `SKIP LOCKED` deadlock avoidance.
3. Capacity planner excludes ACTION_REQUIRED from automated future-step reservations.
4. Per-step `readWaitHours` and `unreadWaitHours` added through database, backend API, Playwright runtime and frontend configuration UI, with 3h/48h backward-compatible defaults.
5. Bot-command foreign keys changed to `ON DELETE CASCADE` for explicit whole-bot deletion.
6. Production's historical hidden 3-first-offer / 1-next-step per-run defaults removed; backend safety controls are authoritative.
7. SEARCH_QUERY model canonicalization and regression coverage expanded across Samsung S/A/M/Tab/Note/XCover/Z Fold/Z Flip families, compact/spaced spellings, plus aliases, generation boundaries and variant boundaries.
8. Exact Vinted model collection-ID parsing supports Vinted's root and `--title` test-id forms while continuing to fail closed for unverified IDs.
9. Existing popup isolation, exact Vinted model source-of-truth semantics, formal-response timers, quota ordering, login safety and terminal listing persistence were re-checked and intentionally preserved.

## Verification gate

The audit changes are mergeable only after all of the following pass on the final PR head:

- Backend unit/context tests.
- Playwright unit tests, including the Samsung SEARCH_QUERY regression matrix and exact-model test-id cases.
- Frontend lint and production build.
- A real PostgreSQL smoke test that applies V30 and V31 against a modeled pre-V30 schema and verifies claim backfill, the new status constraint, bot_command cascades, durable claim survival, 3h/48h defaults and timer CHECK constraints.
- A PostgreSQL execution of the same `FOR UPDATE ... SKIP LOCKED` CTE shape used by pre-submit cross-bot claim release, verifying that a temporary losing row is reopened to DISCOVERED.

No safety invariant above may be weakened merely to make a test pass.
