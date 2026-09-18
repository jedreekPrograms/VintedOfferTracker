# Session preservation after navigation or verification failures

This correction is based on stable `8747bee` and keeps its session preview and
adaptive browser capacity. It does not change credentials, session locations,
offer decisions, account quotas, or the atomic session-file installation and
rotating backup implementation in `SessionManager`.

## Confirmed failure paths

- `MarketplaceNavigator` treated a 15-second `session-refresh` timeout, or an
  incomplete homepage, as a reason to clear the context's cookies and page
  local/session storage. A slow page could therefore force credential login.
  Navigation now keeps the current context state through its bounded retries;
  exhausted retries fail the job without an automatic clean-session reset.
- At job start, `saveSession()` installs the fresh authenticated checkpoint as
  `bot-X.json` and backs up the **previous** file. The old finalization path
  restored that older backup if the final page was unsuitable for saving.
  The new finalizer keeps the current file in that case. Successful,
  authenticated job endings still save their latest state and rotate backups.
  Failures while inspecting the final page also leave the checkpoint intact
  and allow browser cleanup to continue.
- A visible CAPTCHA that remained after the existing 180-second manual window
  used to become a generic job failure, with a 60-second retry for only one job
  type. It now has a distinct exception and a 15-minute cooldown for all jobs
  of that bot in the running scheduler. Other bots remain eligible. The runtime
  reports the manual-verification reason as `RUN_FAILED`; it does not claim an
  explicit rate limit or confirmed blocked session. This CAPTCHA cooldown is
  in the running scheduler; unlike the existing explicit session-block backoff,
  it is not a new persistent backend state across process restarts.

The CAPTCHA detector still requires positive visible evidence. Manual completion
within its existing window continues the same job. There is no CAPTCHA bypass.

## Evidence limits and operation

The supplied log fragment covered 14:06–14:32, after the reported overnight
authentication loss. It confirms the destructive timeout fallback and repeated
CAPTCHA attempts, but cannot establish the first cause of that overnight loss.
Successful later saves and a restored authenticated session are also visible.

After updating stable, rebuild/restart the normal backend and scheduled runtime
with the same session directory and database. Do not delete session files or
automatically copy older backups over them. These changes prevent the described
local session damage; they cannot revive a token the marketplace has revoked.

Regression tests cover stuck and recovering refreshes, partial homepages,
fresh-versus-old checkpoint contents on disk, healthy saves, failed/unfinished
jobs, disappearing CAPTCHA, explicit blocks, and the worker's all-job CAPTCHA
cooldown with browser/permit cleanup and another bot remaining eligible.
