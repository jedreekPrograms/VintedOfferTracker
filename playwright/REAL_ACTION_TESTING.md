# FlipBot scheduler and real-action ladder

Environment variables are read when the Playwright JVM starts. After changing any scheduler or real-action variable, stop the Playwright process completely and start it again.

## Stage 0 - normal observer / scheduler sanity

Keep every real-action flag disabled or unset:

```text
FLIPBOT_REAL_OFFERS_ENABLED=false
FLIPBOT_REAL_NEXT_STEPS_ENABLED=false
FLIPBOT_REAL_ACTION_PREFLIGHT_ONLY=false
FLIPBOT_REAL_ACTION_PRODUCTION_MODE=false
FLIPBOT_REAL_ACTION_ALLOW_ALL_RUNNING_BOTS=false
```

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

1. scheduler reports the expected RUNNING count and active worker slots,
2. different worker slots can have jobs at the same time,
3. the same bot never appears as WORKING in two slots at once,
4. one bot failing does not stop jobs for the other bots,
5. after a job completes, its normal interval is scheduled once, not duplicated.

## Stage 2 - one bot, first-offer PREFLIGHT ONLY

```text
FLIPBOT_REAL_OFFERS_ENABLED=true
FLIPBOT_REAL_NEXT_STEPS_ENABLED=false
FLIPBOT_REAL_ACTION_BOT_IDS=<ONE_TEST_BOT_ID>
FLIPBOT_REAL_ACTION_CONFIRM=I_UNDERSTAND_REAL_ACTIONS
FLIPBOT_REAL_ACTION_PREFLIGHT_ONLY=true
FLIPBOT_REAL_ACTION_PRODUCTION_MODE=false
FLIPBOT_REAL_ACTION_ALLOW_ALL_RUNNING_BOTS=false
```

Restart Playwright. The final submit must not be clicked.

## Stage 3 - one controlled real first offer

Only after Stage 2 is clean:

```text
FLIPBOT_REAL_ACTION_PREFLIGHT_ONLY=false
```

Controlled mode intentionally keeps:

- maximum 1 real first offer per CATALOG_SCAN,
- process-wide first-offer one-shot test lock,
- backend real-action guard/idempotency,
- explicit bot allowlist.

## Stage 4 - one bot, next-step PREFLIGHT ONLY

Use a bot with an existing NEGOTIATING listing and at least two configured negotiation steps:

```text
FLIPBOT_REAL_OFFERS_ENABLED=false
FLIPBOT_REAL_NEXT_STEPS_ENABLED=true
FLIPBOT_REAL_ACTION_BOT_IDS=<ONE_TEST_BOT_ID>
FLIPBOT_REAL_ACTION_CONFIRM=I_UNDERSTAND_REAL_ACTIONS
FLIPBOT_REAL_ACTION_PREFLIGHT_ONLY=true
FLIPBOT_REAL_ACTION_PRODUCTION_MODE=false
FLIPBOT_REAL_ACTION_ALLOW_ALL_RUNNING_BOTS=false
```

The form may be prepared, but submit must not be clicked.

## Stage 5 - one controlled real next step

Only after Stage 4 is clean:

```text
FLIPBOT_REAL_ACTION_PREFLIGHT_ONLY=false
```

Current per-run limit remains maximum 1 real next step per NEGOTIATION_CHECK.

## Stage 6 - controlled multi-bot validation

Start with two explicit bot IDs and one action type at a time. First run PREFLIGHT ONLY, then one controlled real cycle.

```text
FLIPBOT_WORKER_COUNT=2
FLIPBOT_REAL_OFFERS_ENABLED=true
FLIPBOT_REAL_NEXT_STEPS_ENABLED=false
FLIPBOT_REAL_ACTION_BOT_IDS=<BOT_A>,<BOT_B>
FLIPBOT_REAL_ACTION_CONFIRM=I_UNDERSTAND_REAL_ACTIONS
FLIPBOT_REAL_ACTION_PREFLIGHT_ONLY=true
FLIPBOT_REAL_ACTION_PRODUCTION_MODE=false
FLIPBOT_REAL_ACTION_ALLOW_ALL_RUNNING_BOTS=false
```

## Stage 7 - continuous production real actions

Production mode is the explicit switch that removes only the process-wide first-offer one-shot test lock. It does **not** remove backend quota/idempotency, preflight validation, bot-level daily negotiation budgets, or per-run action limits.

### Option A - production for an explicit bot allowlist

```text
FLIPBOT_REAL_OFFERS_ENABLED=true
FLIPBOT_REAL_NEXT_STEPS_ENABLED=true
FLIPBOT_REAL_ACTION_BOT_IDS=3,4,7
FLIPBOT_REAL_ACTION_CONFIRM=I_UNDERSTAND_REAL_ACTIONS
FLIPBOT_REAL_ACTION_PREFLIGHT_ONLY=false
FLIPBOT_REAL_ACTION_PRODUCTION_MODE=true
FLIPBOT_REAL_ACTION_PRODUCTION_CONFIRM=I_UNDERSTAND_CONTINUOUS_REAL_ACTIONS
FLIPBOT_REAL_ACTION_ALLOW_ALL_RUNNING_BOTS=false
```

### Option B - production for every RUNNING bot

This is the scalable mode for installations where bots are created dynamically and should become eligible for real actions simply by being switched to RUNNING in the backend/UI.

```text
FLIPBOT_REAL_OFFERS_ENABLED=true
FLIPBOT_REAL_NEXT_STEPS_ENABLED=true
FLIPBOT_REAL_ACTION_CONFIRM=I_UNDERSTAND_REAL_ACTIONS
FLIPBOT_REAL_ACTION_PREFLIGHT_ONLY=false
FLIPBOT_REAL_ACTION_PRODUCTION_MODE=true
FLIPBOT_REAL_ACTION_PRODUCTION_CONFIRM=I_UNDERSTAND_CONTINUOUS_REAL_ACTIONS
FLIPBOT_REAL_ACTION_ALLOW_ALL_RUNNING_BOTS=true
```

`FLIPBOT_REAL_ACTION_BOT_IDS` can be unset in Option B.

The all-RUNNING scope fails closed unless production mode and its dedicated confirmation token are both valid. A stopped bot is not scheduled by WorkerManager, so it does not receive scheduler jobs.

Expected startup log:

```text
[REAL ACTION CONFIG] PRODUCTION REAL ACTION MODE is armed for scope ALL RUNNING BOTS.
```

Expected real-job log contains:

```text
PRODUCTION REAL ACTION MODE
firstOfferOneShotTestMode=false
```

### Production scheduling defaults

Unless overridden, scheduler defaults are:

```text
FLIPBOT_WORKER_COUNT=10
FLIPBOT_SYNC_INTERVAL_SECONDS=5
FLIPBOT_NEGOTIATION_CHECK_INTERVAL_SECONDS=120
FLIPBOT_CATALOG_SCAN_INTERVAL_SECONDS=900
FLIPBOT_FAILURE_RETRY_SECONDS=60
FLIPBOT_RATE_LIMIT_RETRY_SECONDS=600
FLIPBOT_SHUTDOWN_TIMEOUT_SECONDS=30
FLIPBOT_SCHEDULER_HEADLESS=true
```

`FLIPBOT_WORKER_COUNT` is a concurrency cap, not a bot-count limit. You can have 100 RUNNING bots with 10 worker slots; jobs are queued and shared across those slots. Raising it toward 100 can create many simultaneous browser runtimes and should be load-tested gradually.

### Production invariants that remain enabled

- only scheduler jobs (`CATALOG_SCAN` / `NEGOTIATION_CHECK`) may receive real-action capability,
- real-action preflight still runs before an eligible real job,
- backend guard/idempotency remains authoritative,
- backend negotiation quota and bot daily negotiation budget remain authoritative,
- maximum 1 real first offer per CATALOG_SCAN remains,
- maximum 1 real next step per NEGOTIATION_CHECK remains,
- a single bot is not scheduled concurrently in two worker slots,
- rate-limit and failure retry delays remain active.

## Fail-closed checks

These configurations must remain DRY RUN:

- a requested real action with missing/wrong `FLIPBOT_REAL_ACTION_CONFIRM`,
- explicit scope with empty/invalid `FLIPBOT_REAL_ACTION_BOT_IDS`,
- a non-allowlisted bot in explicit-scope mode,
- `FLIPBOT_REAL_ACTION_PREFLIGHT_ONLY=true`,
- `FLIPBOT_REAL_ACTION_PRODUCTION_MODE=true` with missing/wrong `FLIPBOT_REAL_ACTION_PRODUCTION_CONFIRM`,
- `FLIPBOT_REAL_ACTION_ALLOW_ALL_RUNNING_BOTS=true` without production mode,
- preflight BLOCKED.

## Emergency disarm

Stop Playwright, set:

```text
FLIPBOT_REAL_OFFERS_ENABLED=false
FLIPBOT_REAL_NEXT_STEPS_ENABLED=false
```

and start Playwright again. Real-action configuration is process-start configuration; changing an environment variable without restarting the JVM does not disarm an already-running process.
