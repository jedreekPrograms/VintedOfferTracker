# FlipBot — Vinted Offer Tracker & Negotiation Automation

<p align="center">
  <strong>A full-stack marketplace monitoring system that discovers offers, tracks market activity and manages multi-step price negotiations through an isolated Playwright worker runtime.</strong>
</p>

<p align="center">
  <a href="../../actions/workflows/ci.yml"><img alt="CI" src="https://img.shields.io/github/actions/workflow/status/jedreekPrograms/VintedOfferTracker/ci.yml?branch=main&label=CI"></a>
  <img alt="Java" src="https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white">
  <img alt="Spring Boot" src="https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F?logo=springboot&logoColor=white">
  <img alt="React" src="https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=black">
  <img alt="Playwright" src="https://img.shields.io/badge/Playwright-1.54-2EAD33?logo=playwright&logoColor=white">
  <img alt="PostgreSQL" src="https://img.shields.io/badge/PostgreSQL-17-4169E1?logo=postgresql&logoColor=white">
</p>

> **Status:** active development. FlipBot is an independent engineering project and is not affiliated with, endorsed by, or sponsored by Vinted. Marketplace automation may be subject to platform rules and account restrictions; use it responsibly.

---

## What is FlipBot?

FlipBot is a monorepo built around a simple idea: marketplace monitoring should be **fast, stateful and safe enough to survive real-world UI instability**.

Instead of being a one-off scraper, FlipBot keeps persistent backend state for listings and negotiations, schedules independent browser workers, applies strict target filters, observes market activity and can execute guarded multi-step negotiation flows.

The project currently consists of four cooperating parts:

- **Spring Boot backend** — source of truth for bots, listings, negotiation state, quotas, action guards and market statistics.
- **Playwright automation runtime** — isolated browser jobs for catalog discovery, offer preparation and negotiation checks.
- **React dashboard** — configuration and operational UI.
- **PostgreSQL** — persistent state with Flyway-managed schema migrations.

---

## Demo

> ### 🎬 Demo video — coming soon
>
> Recommended format: **60–120 seconds**, recorded in one continuous flow:
> 1. create/configure a bot,
> 2. show exact model/search targeting,
> 3. start the bot,
> 4. show parallel Playwright workers discovering listings,
> 5. show a negotiation appearing in the dashboard,
> 6. show a seller response and the scheduled next step,
> 7. finish with market statistics.
>
> After uploading the recording (YouTube unlisted, Loom, Vimeo or GitHub-hosted media), replace this block with a thumbnail linked to the video.

### Screenshots

The README is prepared for a small visual gallery. Put final assets in [`docs/media/`](docs/media/) and replace the placeholders below.

| View | What to show |
| --- | --- |
| **Dashboard** | running bots, current status and operational overview |
| **Bot configuration** | target mode, category, brand/model, prices and negotiation ladder |
| **Live discovery** | exact Vinted filter / search-query flow and newest-first scan |
| **Negotiations** | seller activity, current step, waiting policy and next action |
| **Market statistics** | per-model observations and new-listing activity |

**Recommendation for a portfolio README:** keep the video as the strongest proof that the system works, but still add 3–5 screenshots. Recruiters often scan a repository without playing a video.

---

## Highlights

### Two explicit target modes

FlipBot deliberately separates two different ways of identifying a product.

#### `VINTED_MODEL`

Used when Vinted exposes the product as a native model filter.

- category and brand are applied first,
- the model row must be proven as an **exact** visible Vinted option,
- similar variants such as `Edge`, `Ultra`, `FE` or `+` are not accepted as a substitute,
- the resulting `brand_collection_ids[]` value is verified end-to-end,
- after an exact Vinted filter is persisted, Vinted's own model classification is treated as the source of truth.

This avoids trying to “repair” Vinted's classification by reinterpreting seller-written titles.

#### `SEARCH_QUERY`

Used when there is no suitable native Vinted model filter.

- the requested model is entered into Vinted search,
- category / brand / price constraints are still applied,
- results are semantically verified against listing title, URL and — when necessary — the live item page,
- wrong generations, variants and common accessories are rejected.

The distinction between these modes is preserved through discovery, market statistics and negotiation verification.

---

## Negotiation engine

Listings are not treated as disposable scraper output. They move through a persistent lifecycle in the backend.

A typical flow looks like this:

```text
DISCOVERED
   │
   ├─ unavailable/sold ───────────────► UNAVAILABLE
   ├─ wrong SEARCH_QUERY target ──────► SKIPPED_TARGET_MISMATCH
   ├─ no supported negotiation action ► SKIPPED_CANNOT_NEGOTIATE
   │
   ▼
NEGOTIATING
   │
   ├─ seller activity detected
   ├─ configured waiting policy
   ├─ next negotiation step
   └─ terminal marketplace state
```

### Stateful follow-ups

The backend remembers, among other things:

- marketplace listing ID,
- Vinted conversation ID / URL,
- current negotiation step,
- current offer price,
- whether the bot is awaiting a seller response,
- detected seller activity timestamps,
- formal seller responses such as counteroffers,
- persistent real-action audit / guard state.

A browser job can therefore end and a later job can continue the same negotiation without relying on in-memory browser state.

### Timing policy

Negotiation checks are scheduler-driven. A seller message does **not** automatically cause an immediate reply: the decision layer can wait for the configured response window and only continue after the corresponding deadline.

---

## Safety around real actions

The automation runtime separates *preparation* from *submission*.

For a first offer, the important order is:

1. verify the target and listing state,
2. open the listing,
3. prepare the offer form,
4. verify the price and enabled submit action,
5. acquire the persistent action guard,
6. reserve quota immediately before submission,
7. click the real submit action,
8. confirm the resulting conversation,
9. persist backend state and audit information.

This minimizes ambiguous states around retries and prevents an ordinary page/load failure from consuming quota before the action is ready.

Additional protections include:

- per-bot daily offer quotas,
- persistent first-offer / next-step action guards,
- action audit records,
- rate-limit detection,
- unavailable-item detection,
- target mismatch persistence,
- bounded detail-page inspection,
- bounded per-run real actions,
- fail-closed behavior when an exact model cannot be proven.

---

## Parallel worker architecture

FlipBot does **not** run every bot serially in one browser.

The scheduler owns multiple worker slots. Different bots can execute simultaneously, while jobs belonging to the **same bot** remain serialized to avoid two browser jobs mutating one negotiation state at the same time.

```mermaid
flowchart LR
    UI[React Dashboard] --> API[Spring Boot API]
    API --> DB[(PostgreSQL)]

    PW[Playwright Scheduler] --> API
    PW --> W1[Worker Slot 1]
    PW --> W2[Worker Slot 2]
    PW --> WN[Worker Slot N]

    W1 --> V[Vinted]
    W2 --> V
    WN --> V

    OBS[Market Stats Observer] --> API
    OBS --> V
```

Each scheduled bot job uses an isolated `BrowserContext`. The worker-slot Playwright runtime can stay alive between jobs, but cookies/storage and page lifetime remain isolated at job level.

### Single-page browser policy

Automation flows intentionally use one main Vinted page. Additional tabs/windows — including advertising or RTB redirects — are closed immediately at page creation so they cannot take over the visible automation flow.

---

## Market statistics observer

A separate read-only collector tracks listing activity per dictionary model.

For each target it can record:

- known marketplace listing IDs,
- newly observed listing IDs,
- baseline completion state,
- bounded newest-first scans,
- model-level activity over time.

The observer is independent from normal bot worker slots. It can use a restored authenticated session when available, but public catalog collection does not depend on a successful interactive login.

For `VINTED_MODEL` targets, observer scans remain strict: if the exact native model filter cannot be established, the target fails closed rather than silently switching to text search.

---

## Browser resilience

Vinted is a dynamic client-rendered application, so the Playwright layer contains explicit recovery mechanisms rather than assuming every click immediately changes the URL.

Examples:

- category selection retry and page reset,
- brand persistence verification,
- exact-model row verification,
- exact model collection-ID persistence retry,
- safe recovery to a known Vinted URL,
- popup/tab isolation,
- human-verification waiting hooks,
- item availability checks,
- graceful retry scheduling after transient failures.

The goal is not to hide failures. Important failures remain visible in structured logs while unsafe fallbacks are rejected.

---

## Tech stack

| Layer | Technology |
| --- | --- |
| Backend | Java 21, Spring Boot 4.1, Spring MVC, Spring Data JPA, Validation, Security |
| Database | PostgreSQL 17, Flyway |
| Automation | Java 21, Microsoft Playwright 1.54 |
| Frontend | React 19, TypeScript, Vite 8, React Router |
| Tooling | Maven, npm, Docker Compose, GitHub Actions |

---

## Repository structure

```text
VintedOfferTracker/
├── backend/        # Spring Boot API, persistence, business rules, Flyway
├── frontend/       # React + TypeScript dashboard
├── playwright/     # browser workers, filters, scanner, negotiations, observer
├── docker/         # local PostgreSQL compose setup
├── docs/media/     # screenshots / demo assets for this README
└── .github/        # CI workflow
```

---

## Running locally

### Requirements

- Java 21
- Node.js / npm
- Docker + Docker Compose
- Chromium installed by Playwright when required

### 1. Start PostgreSQL

```bash
cd docker
docker compose up -d
```

The development compose file exposes PostgreSQL on `localhost:5433` and creates the `flipbot` database.

### 2. Start the backend

Linux/macOS:

```bash
cd backend
./mvnw spring-boot:run
```

Windows:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Flyway migrations are applied by the backend during startup.

### 3. Start the frontend

```bash
cd frontend
npm install
npm run dev
```

### 4. Start the Playwright runtime

```bash
cd playwright
mvn test
```

For local development, run `pl.flipbot.playwright.FlipBotPlaywrightApplication` from the IDE (or package/run the Java module with your preferred Maven execution setup). Runtime behavior such as headless mode, scheduler cadence and real-action permissions should be configured deliberately before using an account.

---

## Tests & CI

GitHub Actions validates all major modules on pull requests:

- **Frontend** — dependency install, ESLint and production build,
- **Backend** — Maven test suite against PostgreSQL,
- **Playwright** — Java unit tests for filtering, targeting, negotiation and worker logic.

Useful local checks:

```bash
# backend
cd backend
./mvnw test

# playwright
cd playwright
mvn test

# frontend
cd frontend
npm install
npm run lint
npm run build
```

---

## Design principles

### Fail closed on identity

A failed exact model verification is preferable to negotiating the wrong product variant.

### Persist business state outside the browser

Browser contexts are disposable. Negotiation state belongs in PostgreSQL/backend APIs.

### Prepare before reserving scarce actions

Quota and action guards are acquired as late as safely possible — immediately before a real submit.

### Distinguish transient UI failure from business state

A missing button once is not automatically a permanent seller/account restriction; transient conditions are retried before a terminal status is persisted.

### Keep history instead of deleting it

Unavailable or sold listings are represented by terminal statuses rather than being deleted, preserving auditability and preventing the same listing from being rediscovered as “new”.

---

## Suggested demo script

If you are reviewing this repository as a portfolio project, the most useful demo is a short end-to-end recording:

1. **Dashboard** — show two configured bots.
2. **Target modes** — native exact model vs `SEARCH_QUERY`.
3. **Parallelism** — start both and show two worker slots in logs.
4. **Discovery** — show a newest-first scan and backend assignment.
5. **Real-action safeguards** — show preparation before quota/guard acquisition.
6. **Negotiation memory** — stop a browser job, then show the next scheduled job continuing the same conversation from persisted state.
7. **Market stats** — show observer scans running independently.
8. **Failure handling** — briefly show an unavailable/sold item being terminally classified instead of retried forever.

Do not make the video a long code walkthrough. The README already explains architecture; the video should prove behavior.

---

## Roadmap

- richer dashboard observability for worker/job state,
- per-model market activity charts,
- improved operational metrics and structured tracing,
- broader automated regression coverage for marketplace UI changes,
- deployment packaging / environment profiles,
- polished public demo video and screenshot gallery.

---

## Disclaimer

This repository is an independent software-engineering project. It is not an official Vinted client and is not affiliated with Vinted. UI selectors and marketplace behavior can change without notice. Anyone running marketplace automation is responsible for applicable platform terms, account safety and local law.
