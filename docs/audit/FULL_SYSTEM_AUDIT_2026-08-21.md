# Full System Integrity Audit — 2026-08-21

This document records the invariants verified during the full FlipBot audit and the fixes applied by the audit branch.

## Core invariants

- Marketplace discovery is persisted per bot, so each bot owns an independent DISCOVERED backlog.
- A marketplace listing may be discovered by multiple bots, but only one bot may ever acquire the durable right to start a real negotiation for the same marketplace/listing identity.
- A real FIRST_OFFER is guarded before quota reservation and before the submit action.
- Ambiguous post-submit failures fail closed: quota/guard/marketplace claim are not silently released.
- Daily quota counts real actions, while negotiation capacity separately reserves future automated steps for NEGOTIATING conversations.
- ACTION_REQUIRED is manual and therefore does not reserve future automated negotiation steps.
- Sold/unavailable/terminal listings remain persisted; routine runtime logic never deletes them merely because they became terminal.
- VINTED_MODEL trusts the exact persisted Vinted collection filter and does not reinterpret seller-written titles.
- SEARCH_QUERY keeps semantic generation/variant verification.
- Read/no-read follow-up delays belong to the current negotiation step and are persisted/configurable independently.
- When a read/no-read timer elapses, the runtime advances to the next configured step when one exists; only an exhausted/capped ladder becomes EXPIRED.
- Controlled real-action mode remains one action. Fully armed production throughput is governed by backend planner/quota/action guards unless an operator explicitly configures a lower runtime throttle.
- Deleting a bot must not be blocked by historical bot_command foreign keys.

## Audit fixes

1. Durable cross-bot marketplace negotiation claims and terminal `SKIPPED_ALREADY_NEGOTIATED` loser state.
2. Capacity planner excludes ACTION_REQUIRED from automated future-step reservations.
3. Per-step `readWaitHours` and `unreadWaitHours` added through database, backend API, Playwright runtime and frontend configuration UI, with 3h/48h backward-compatible defaults.
4. Bot-command foreign keys changed to `ON DELETE CASCADE` for explicit whole-bot deletion.
5. Production's historical hidden 3-first-offer / 1-next-step per-run defaults removed; backend safety controls are authoritative.
6. Dedicated SEARCH_QUERY regressions for Galaxy Tab S11 Ultra and confusing adjacent variants.
7. Existing popup isolation, exact Vinted model identity, formal-response timers, quota ordering and terminal listing persistence were re-checked and intentionally preserved.

## Verification gate

The audit changes are mergeable only after Backend, Playwright and Frontend CI jobs all pass. No safety invariant above may be weakened merely to make a test pass.
