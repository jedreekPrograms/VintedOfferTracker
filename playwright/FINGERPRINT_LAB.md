# FlipBot Fingerprint Defense Lab

This module is a **local defensive laboratory** for studying browser fingerprinting and tamper detection. It is intentionally separate from FlipBot's marketplace runtime and is not wired into `BrowserManager`, `BotContext`, login, scanning or negotiation flows.

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

## Important separation from FlipBot production automation

The lab code lives under:

```text
pl.flipbot.playwright.lab.fingerprint
```

No production browser class imports it. Do not move the simulator into `BrowserManager`, `BotContext`, marketplace login or worker code. The laboratory is designed to be useful precisely because it can be run and inspected without changing the behavior of real marketplace bots.
