# Adaptive runtime capacity

Scheduled bot jobs and the market-statistics observer share a local browser budget.
The scheduler still executes only one job per bot, preserves negotiation/catalog
intervals, and keeps account quotas, session-block cooldowns and rate-limit waits.
Session preview uses the same budget and the same next scheduled job.

The adaptive budget starts at three browsers (or a lower configured ceiling).
It can grow by one after 30 seconds of healthy measurements and queued demand:
system CPU below 75%, and free physical memory above the reserve plus two estimated
browser launches. The reserve is the larger of 1536 MiB and 10% of physical memory;
each newly launched browser provisionally reserves another 768 MiB for ten seconds.
Starts are spaced by at least two seconds. These are conservative admission
heuristics, not a measurement or guarantee of each browser's peak memory.

At CPU usage of 90% or memory below the reserve, new work waits and the target
decreases. Already-running jobs finish normally. Missing OS measurements prevent
growth beyond three. A failed admission is a local queue deferral, not a failed
job: it does not increment failures or start a browser. Permits remain held until
browser cleanup completes. The observer acquires a permit before its full pass,
retains it during its sequential browser recycling, and releases it after the
pass, so waiting for resources does not skip or restart a model midway.

An explicit marketplace rate limit or session block reduces the shared target
and pauses growth for at least ten minutes. Account-specific retry policies still
apply. The observer retains its existing traffic-backoff policy. No account is
polled more frequently by changing its configured normal interval, and no new
CAPTCHA bypass or fingerprint-masking mechanism is introduced. Higher aggregate
parallelism can still increase traffic and cannot guarantee absence of blocks.

| Variable | Default | Meaning |
|---|---|---|
| `FLIPBOT_ADAPTIVE_CONCURRENCY` | `true` | Set to `false` to restore the previous independent worker/catalog limits. |
| `FLIPBOT_MAX_BROWSER_JOBS` | Logical CPU count, capped at 16 | Upper ceiling for all scheduled browsers including the observer. Actual concurrency starts lower and is controlled by measurements. |
| `FLIPBOT_WORKER_COUNT` | Adaptive ceiling; 10 in compatibility mode | Maximum ordinary worker slots. An explicit existing setting is preserved. |
| `FLIPBOT_MAX_CONCURRENT_CATALOG_SCANS` | Adaptive ceiling; 3 in compatibility mode | Scheduler ceiling for catalog jobs. An explicit setting such as `3` remains authoritative. |

For a Ryzen 9 8945HS exposing 16 logical CPUs, automatic mode can grow from 3
toward a ceiling of 16 **total scheduled browsers**, if RAM, CPU, demand and service
backoff permit. This is not a promise that a particular laptop can sustain 16.
The configuration does not allocate 16 browsers eagerly. Ordinary slots are still
created only as required by the number of RUNNING bots, and browsers close after
each job. RAM size and other applications can keep the effective limit much lower.
The manual single-run test entry point is outside the scheduled admission budget.
The controller coordinates one runtime process; do not run duplicate scheduler
processes for the same set of accounts.

The `[BROWSER CAPACITY]` log records configured ceiling, active/target counts,
free memory and CPU readings at admission, and the reason for target changes.
Compare completed jobs and queue delays as well as memory: reducing RAM by merely
postponing work is not evidence that an individual job got faster.

Other changes reduce work without weakening business guards:

- Healthy session startup does not wait five seconds for an absent cookie button.
  Existing context-level consent handling remains installed for late banners.
- Existing negotiations are fetched once per account/job and filtered into immutable
  product snapshots. Actual action guards and quota checks still run.
- Worker synchronization and runtime views read scalar bot projections, excluding
  the dedicated observer as before.
- Dashboard counters use SQL aggregation; money reads only purchase-price pairs
  and retains the previous BigDecimal rounding. History filters/sorts in SQL and
  reads display projections, including product provenance and undated entries.
- The Bots page uses one bulk runtime request plus bounded daily-activity reads,
  pauses polling while hidden, serializes refresh cycles and aborts obsolete work.
  Daily quota enforcement and repair logic are unchanged.

No schema migration is needed. SessionManager, saved session files, last-known-good
restoration, target/category matching, offer limits and negotiation decisions are
not changed by this performance patch. The CAPTCHA changes from stable PR #235
are included in its base.

## Windows setup

Stop the backend and the scheduled runtime using their normal stop controls before
updating; allow an in-progress action to finish. Keep local session files, database
and existing secret configuration. Update only by fast-forward, never reset/clean:

```powershell
Set-Location 'C:\Users\jedre\Desktop\flipbot'
git status --short
git switch feat/mobile-monitor-stable-100ebca
git pull --ff-only origin feat/mobile-monitor-stable-100ebca
```

If Git reports local changes that conflict, keep them and resolve the update first.
The new defaults already enable automatic capacity. If an old environment override
still pins worker/catalog capacity to 3 or 10, remove that override from the runtime's
IDE run configuration, or set these values **in the PowerShell process that starts
the scheduled runtime**:

```powershell
$env:FLIPBOT_ADAPTIVE_CONCURRENCY = 'true'
$env:FLIPBOT_MAX_BROWSER_JOBS = '16'
$env:FLIPBOT_WORKER_COUNT = '16'
$env:FLIPBOT_MAX_CONCURRENT_CATALOG_SCANS = '16'
```

Setting these in an unrelated shell does not change an already-running IDE or
worker. Restart the normal `pl.flipbot.playwright.FlipBotPlaywrightApplication`
entry point and the updated backend. Use the same existing credentials and session
locations. Keep the previous normal job intervals and marketplace cooldowns.

For an isolated compatibility check, start a new runtime with:

```powershell
$env:FLIPBOT_ADAPTIVE_CONCURRENCY = 'false'
$env:FLIPBOT_WORKER_COUNT = '10'
$env:FLIPBOT_MAX_CONCURRENT_CATALOG_SCANS = '3'
```

This reverts only scheduling capacity behavior; the lighter data reads remain.
