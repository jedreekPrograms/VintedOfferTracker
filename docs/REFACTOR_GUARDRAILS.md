# FlipBot refactor guardrails (2026-10-09)

## Immutable starting point

- Source branch: `main`, starting commit: `69168eed4c9dcb40bc861d68361be0e045599373`.
- Preserved backup branch: `backup/pre-refactor-20261009-main`.
- Working branch: `refactor/architecture-cleanup-20261009`.
- **Never merge or modify `main` as part of refactor work without review and green tests.**
- The backup branch is a rollback reference, not a target for new commits.

## Product-matching behavior (must not regress)

### Native model filter: `VINTED_MODEL`

1. Select an **exact** visible native Vinted model and verify the selected collection ID persists in the catalog URL. An S25 option must not silently match S25 FE, Ultra, Edge, etc.
2. **Trust listings actually present in the CURRENT native filtered scan**, even when the seller-written title looks inconsistent with the target model. Do not run SEARCH_QUERY semantic matching on these listings.
3. A stored/backlog listing that is **not** present in that current verified scan is **deferred until a fresh native-filter scan rechecks it**. It may have been sold, removed, changed or become unavailable. Do not promote stale provenance to current proof and do not run seller-title heuristics to reclassify it. Preserve all existing listing-availability checks and terminal-state transitions.
4. Safety checks unrelated to seller-title model classification still apply: listing/action identity, availability, account/session restrictions, marketplace ownership, quotas, idempotency and persistence.

Key implementation: `NewNegotiationProcessor.retainTargetEligibleListings`, `hasCurrentExactModelProof`, `FilterActions.confirmExactModelWithRetry`, `AdaptiveFirstOfferExecutor.applyLiveTargetConsistencyGuard`.

### Search mode: `SEARCH_QUERY`

1. Treat free-text search results as **untrusted** model identity.
2. Keep the existing catalog-title, URL, cached/live item-detail and pre-submit target checks, especially differentiation between model generations, variants and accessories.
3. Unknown/ambiguous evidence must not cause a potentially wrong real offer.

## Real-action integrity (must not regress)

- Preserve the order of preparation, ownership claim, durable action guard, daily quota reservation, submit, verification, audit and durable state update.
- Do not allow duplicated submissions after errors, retries, scheduler/process restarts or across bots.
- Preserve persisted cooldown/backoff, session restoration, worker retirement, locking and scheduler WORKING-state completion.
- Keep dry runs/read-only previews separate from real submissions and persistence.
- Keep multi-product bot scheduling and negotiation capacities intact.

## How to refactor safely

1. First remove unreferenced legacy code with a repository-wide reference check.
2. Extract duplication only after comparing exact behavior and adding tests for the affected paths.
3. Prefer stable data-testid, role and scoped locators over DOM-wide text scans, but do **not** remove a fallback without proving the relevant Vinted DOM variants and account states work.
4. Measure job time, navigation, retries and database queries rather than assuming a selector/algorithm dominates runtime.
5. Keep PRs small; CI is a prerequisite, not proof of live compatibility.
6. Test separately: native model filtering, text-search matching, first/next offer, quota/guard/ownership, session refresh, cooldown, scheduler retirement and market observer.

## Verification commands

```bash
cd backend && ./mvnw -B test
cd ../playwright && mvn -B test
cd ../frontend && npm ci && npm run lint && npm run build
```

Note: this repository's standard CI runs on pull requests. The local Playwright runtime may also depend on the optional private core artifact; unit tests alone do not prove live Vinted behavior.
