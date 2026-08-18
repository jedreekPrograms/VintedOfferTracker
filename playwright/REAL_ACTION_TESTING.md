# FlipBot scheduler and real-action test ladder

This checklist is intentionally staged. Do not skip directly from DRY RUN to multi-bot real actions.

Environment variables are read when the Playwright JVM starts. After changing any real-action flag, stop the Playwright process completely and start it again.

## Stage 0 - normal observer / scheduler sanity

Keep every real-action flag disabled or unset:

```text
FLIPBOT_REAL_OFFERS_ENABLED=false
FLIPBOT_REAL_NEXT_STEPS_ENABLED=false
FLIPBOT_REAL_ACTION_PREFLIGHT_ONLY=false
```

`FLIPBOT_REAL_ACTION_BOT_IDS` and `FLIPBOT_REAL_ACTION_CONFIRM` can remain unset.

Expected log mode:

```text
[REAL ACTION CONFIG] Scheduled real actions are disabled. All scheduler jobs remain DRY RUN.
[SCHEDULED JOB] ... DRY RUN mode. realOffers=false, realNextSteps=false.
```

Do not continue if there are scheduler crashes, repeated login failures, or final market-stat failures.

## Stage 1 - scheduler multithreading, still DRY RUN

Use at least 3 RUNNING bots if possible and set:

```text
FLIPBOT_WORKER_COUNT=3
FLIPBOT_REAL_OFFERS_ENABLED=false
FLIPBOT_REAL_NEXT_STEPS_ENABLED=false
FLIPBOT_REAL_ACTION_PREFLIGHT_ONLY=false
```

Nothing can be submitted in this stage.

Verify:

1. scheduler reports `RUNNING=3` and `activeSlots=3` (or the actual number of RUNNING bots if lower),
2. different `flipbot-worker-slot-N` threads can have `working` jobs at the same time,
3. the same bot never appears as WORKING in two slots at once,
4. one bot failing does not stop jobs for the other bots,
5. after a job completes, its normal interval is scheduled once, not duplicated.

Keep this stage running through several catalog scans and negotiation checks before arming any real action.

## Stage 2 - one bot, first-offer PREFLIGHT ONLY

Choose exactly one test bot ID. Keep next steps disabled:

```text
FLIPBOT_REAL_OFFERS_ENABLED=true
FLIPBOT_REAL_NEXT_STEPS_ENABLED=false
FLIPBOT_REAL_ACTION_BOT_IDS=<ONE_TEST_BOT_ID>
FLIPBOT_REAL_ACTION_CONFIRM=I_UNDERSTAND_REAL_ACTIONS
FLIPBOT_REAL_ACTION_PREFLIGHT_ONLY=true
```

Restart Playwright.

Expected:

- `[REAL ACTION PREFLIGHT] READY` for the selected bot and CATALOG_SCAN,
- `PREFLIGHT ONLY / DRY RUN`,
- target verification, quota/capacity checks and offer preparation may run,
- the final submit must not be clicked.

If preflight says BLOCKED, fix the reported reason instead of disabling safeguards.

## Stage 3 - one controlled real first offer

Only after Stage 2 is clean, change one variable:

```text
FLIPBOT_REAL_ACTION_PREFLIGHT_ONLY=false
```

Keep:

```text
FLIPBOT_REAL_OFFERS_ENABLED=true
FLIPBOT_REAL_NEXT_STEPS_ENABLED=false
FLIPBOT_REAL_ACTION_BOT_IDS=<ONE_TEST_BOT_ID>
FLIPBOT_REAL_ACTION_CONFIRM=I_UNDERSTAND_REAL_ACTIONS
```

Restart Playwright.

Current safety limits intentionally remain:

- maximum 1 real first offer per CATALOG_SCAN run,
- one-shot first-offer test mode remains enabled,
- backend real-action guard/idempotency remains active,
- only allowlisted bot IDs can submit.

After confirming one real offer behaved correctly, DISARM first offers and restart:

```text
FLIPBOT_REAL_OFFERS_ENABLED=false
FLIPBOT_REAL_ACTION_PREFLIGHT_ONLY=false
```

You may also remove `FLIPBOT_REAL_ACTION_CONFIRM` and `FLIPBOT_REAL_ACTION_BOT_IDS` while disarmed.

## Stage 4 - one bot, next-step PREFLIGHT ONLY

Use a bot with an existing NEGOTIATING listing and at least two configured negotiation steps.

```text
FLIPBOT_REAL_OFFERS_ENABLED=false
FLIPBOT_REAL_NEXT_STEPS_ENABLED=true
FLIPBOT_REAL_ACTION_BOT_IDS=<ONE_TEST_BOT_ID>
FLIPBOT_REAL_ACTION_CONFIRM=I_UNDERSTAND_REAL_ACTIONS
FLIPBOT_REAL_ACTION_PREFLIGHT_ONLY=true
```

Restart Playwright.

Verify:

- preflight is READY,
- the correct existing conversation is opened,
- the policy selects the expected next step and amount,
- the form is prepared,
- submit is NOT clicked.

## Stage 5 - one controlled real next step

Only after Stage 4 is clean:

```text
FLIPBOT_REAL_ACTION_PREFLIGHT_ONLY=false
```

Keep first offers disabled.

Restart Playwright and allow one eligible NEGOTIATION_CHECK to run.

Current limit remains maximum 1 real next step per NEGOTIATION_CHECK run.

After confirming the action, DISARM and restart:

```text
FLIPBOT_REAL_NEXT_STEPS_ENABLED=false
```

## Stage 6 - controlled multi-bot real-action concurrency

Do this only after both single-bot real-action tests passed.

Start with two bot IDs and ONE action type at a time. Example for first offers:

```text
FLIPBOT_WORKER_COUNT=2
FLIPBOT_REAL_OFFERS_ENABLED=true
FLIPBOT_REAL_NEXT_STEPS_ENABLED=false
FLIPBOT_REAL_ACTION_BOT_IDS=<BOT_A>,<BOT_B>
FLIPBOT_REAL_ACTION_CONFIRM=I_UNDERSTAND_REAL_ACTIONS
FLIPBOT_REAL_ACTION_PREFLIGHT_ONLY=true
```

First run it in PREFLIGHT ONLY. Verify two different worker slots can prepare work concurrently and each bot stays isolated in its own BrowserContext/session.

Then, if clean, set `FLIPBOT_REAL_ACTION_PREFLIGHT_ONLY=false`, restart, and observe one controlled run. Per-run limits still apply independently to each bot.

DISARM immediately after the test and restart.

Do not test first offers and next steps simultaneously until each action type has separately passed the two-bot concurrency test.

## Fail-closed checks worth testing once

These should all stay DRY RUN:

- real action requested but `FLIPBOT_REAL_ACTION_CONFIRM` missing,
- wrong confirmation token,
- `FLIPBOT_REAL_ACTION_BOT_IDS` empty,
- allowlist containing an invalid/blank/non-positive ID,
- requested action for a bot that is not in the allowlist,
- preflight BLOCKED,
- `FLIPBOT_REAL_ACTION_PREFLIGHT_ONLY=true`.

## Not a production switch

The current main branch intentionally keeps controlled one-shot/per-run limits. This checklist is for validation before any separate decision about continuous production real actions.
