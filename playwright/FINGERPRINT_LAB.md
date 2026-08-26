# FlipBot Fingerprint Defense Lab

This module is a **local defensive laboratory** for studying browser fingerprinting and tamper detection. It has its own standalone launcher and also exposes an explicitly gated bridge into FlipBot's regular `BrowserManager` pipeline for testing the same browser-context path against laboratory URLs. Login, scanning, negotiation, workers and marketplace navigation are otherwise unchanged.

## Safety boundary

The lab is disabled by default and requires:

```text
FLIPBOT_FINGERPRINT_LAB=true
```

Even with the flag enabled, the target URL must be one of:

- `localhost`
- `127.0.0.1`
- `::1`
- `*.localhost`
- `*.test`

HTTP(S) requests to other hosts are aborted. Service Workers are blocked. WebSockets are routed separately and only loopback / reserved test-domain sockets are allowed to connect. This means Vinted and arbitrary production websites are rejected by policy rather than by convention.

## What the simulator changes

The synthetic demo profile intentionally leaves obvious laboratory evidence. It demonstrates common spoofable surfaces without trying to hide that they were modified:

- `navigator.platform`
- `navigator.hardwareConcurrency`
- `navigator.deviceMemory`
- `navigator.language` / `navigator.languages`
- `navigator.maxTouchPoints`
- `screen.width` / `screen.height`
- available screen size
- color depth
- `devicePixelRatio`
- locale and timezone through a dedicated Playwright context
- WebGL vendor / renderer surface
- deterministic canvas-output perturbation

The simulator deliberately **does not forge native function source** and installs `window.__flipbotFingerprintLab`, so defensive checks can detect that hooks exist.

## What the detector observes

The included page checks multiple independent signal families:

- legacy navigator values
- User-Agent Client Hints when available
- screen / DPR
- timezone and UTC offset
- WebGL
- canvas repeatability and API integrity
- `navigator.webdriver`
- media-device surface
- WebGPU / WebRTC / WebAssembly capability presence
- Worker-vs-window CPU consistency
- whether supposedly native getters/functions still look native

The key defensive lesson is **consistency**. A single value is weak evidence. A set of values that contradict independent browser realms, UA-CH, rendering APIs or network-level observations is much stronger evidence of tampering.

## Run the built-in lab on Windows PowerShell

From the repository:

```powershell
cd C:\Users\jedre\Desktop\flipbot\playwright
$env:FLIPBOT_FINGERPRINT_LAB="true"

mvn test

mvn -DskipTests compile org.codehaus.mojo:exec-maven-plugin:3.6.3:java `
  "-Dexec.mainClass=pl.flipbot.playwright.lab.fingerprint.FingerprintLabApplication"
```

With no custom URL, the application starts its own loopback detector server on:

```text
http://127.0.0.1:18091/
```

It first records a baseline Chrome fingerprint, then opens the synthetic laboratory context and prints both snapshots to the terminal. Press Enter in the terminal to close the lab.

## Run against your own test page

Your test page must still be on an allowed laboratory host. For example:

```powershell
$env:FLIPBOT_FINGERPRINT_LAB="true"
$env:FLIPBOT_FINGERPRINT_LAB_URL="http://localhost:5173/fingerprint-test"

mvn -DskipTests compile org.codehaus.mojo:exec-maven-plugin:3.6.3:java `
  "-Dexec.mainClass=pl.flipbot.playwright.lab.fingerprint.FingerprintLabApplication"
```

Assets and API calls to another allowed loopback / `*.test` URL are allowed. Production CDNs, analytics endpoints and external WebSockets are blocked by the lab network boundary.

## Guarded integration with FlipBot BrowserManager

The regular `BrowserManager.createContext(...)` path can use the same laboratory profile, but only when all three conditions are present:

```text
FLIPBOT_FINGERPRINT_RUNTIME_INTEGRATION=true
FLIPBOT_FINGERPRINT_LAB=true
FLIPBOT_FINGERPRINT_LAB_URL=<allowed laboratory URL>
```

For example, a local test target may be configured as:

```powershell
$env:FLIPBOT_FINGERPRINT_RUNTIME_INTEGRATION="true"
$env:FLIPBOT_FINGERPRINT_LAB="true"
$env:FLIPBOT_FINGERPRINT_LAB_URL="http://127.0.0.1:18091/"
```

When the runtime bridge is active:

- `BrowserManager` applies the laboratory locale/timezone/viewport/DPR context options before context creation;
- Service Workers are blocked;
- the existing laboratory HTTP(S) and WebSocket safety boundary is installed;
- the synthetic fingerprint init script is installed before documents load;
- the existing Vinted informational-dialog guard and normal session logic remain in place;
- the context is still unable to reach Vinted or arbitrary production hosts because the allowlist/network boundary is unchanged.

If the runtime-integration flag is absent, normal FlipBot behavior is unchanged. If integration is requested but the lab feature flag is missing, the target is missing, or the target is not on the laboratory allowlist, context creation fails closed.

The bridge does **not** change `MarketplaceNavigator` or redirect production flows to a test site. It only makes the regular browser-context creation path laboratory-capable when explicitly configured.

## Code layout

Laboratory code lives under:

```text
pl.flipbot.playwright.lab.fingerprint
```

The guarded integration entry point is:

```text
FingerprintLabRuntimeIntegration
```

`BrowserManager` calls that bridge while the rest of the production marketplace workflow remains unchanged. Keep the allowlist and network boundary intact when extending the laboratory.
