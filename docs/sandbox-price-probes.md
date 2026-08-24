# Sandbox price probes

`PRICE_PROBE` is an opt-in test mode for a separately hosted Vinted-compatible sandbox/clone.
It reuses the existing FlipBot/Vinted selectors so a 1:1 test UI can exercise the same browser flow,
but the module is not allowed to navigate to `vinted.pl` or any `*.vinted.pl` host.

## Enable

```text
FLIPBOT_PRICE_PROBE_ENABLED=true
FLIPBOT_PRICE_PROBE_BASE_URL=https://your-sandbox.example
```

Optional:

```text
FLIPBOT_PRICE_PROBE_INTERVAL_SECONDS=300
FLIPBOT_PRICE_PROBE_MAX_PER_JOB=1
```

`FLIPBOT_PRICE_PROBE_BASE_URL` accepts HTTPS. HTTP is accepted only for localhost/127.0.0.1 local development.
The base URL cannot contain a path, query string, fragment or user info.

A source listing may still have a production-shaped URL such as
`https://www.vinted.pl/items/123-example`. The probe executor never opens that host. It takes only the
path/query (`/items/123-example`) and maps them onto the configured sandbox base URL before navigation.

## Assignment rules

- source must currently be `NEGOTIATING` or `ACTION_REQUIRED`;
- probe bot and source bot must be different bots;
- both bots must represent the same marketplace/category/brand/model or search target;
- one probe bot can claim a source listing only once;
- one source listing accepts at most 15 probe claims;
- claim is persisted before browser execution, so an ambiguous send is never automatically retried;
- generated text contains an explicit `PLN` amount;
- the executor uses only the text-message composer and never clicks the official price-offer controls.

The feature is disabled by default. With `FLIPBOT_PRICE_PROBE_ENABLED` unset/false the scheduler does not queue `PRICE_PROBE` jobs.
