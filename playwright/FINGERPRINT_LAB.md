# FlipBot Fingerprint Lab v2

This module is one consolidated **local / reserved-test laboratory** for studying browser fingerprint consistency, session isolation, first-party cookie lifecycle, bounded multi-context scale, network-path observations and human-interaction simulations.

It is built on top of the original fingerprint lab and the guarded `BrowserManager` integration. The safety boundary is intentionally stronger than a feature switch: production hosts remain unreachable from a lab-instrumented context.

## Hard safety boundary

The lab is disabled by default and requires:

```text
FLIPBOT_FINGERPRINT_LAB=true
```

Allowed target hosts are only:

```text
localhost
127.0.0.1
::1
*.localhost
.test
*.test
```

HTTP(S) traffic to other hosts is aborted. WebSockets are independently restricted to the same laboratory host classes. Service Workers are blocked.

Optional proxy routing is also restricted to loopback / reserved test hosts. A production residential/mobile proxy URL is rejected even when every feature flag is enabled.

Vinted and arbitrary production websites therefore fail closed by policy rather than by convention.

## Consolidated capabilities

### Coherent profile catalog

Available synthetic profiles:

```text
windows-desktop-pl
windows-laptop-pl
windows-desktop-en
```

Select one with:

```text
FLIPBOT_FINGERPRINT_LAB_PROFILE
```

Each profile keeps related values together: platform, CPU count, memory, language order, touch points, screen/available screen geometry, DPR, timezone and WebGL identity.

### Fingerprint surfaces

The laboratory currently demonstrates:

```text
navigator.platform
navigator.hardwareConcurrency
navigator.deviceMemory
navigator.language / navigator.languages
navigator.maxTouchPoints
screen.width / height
screen.availWidth / availHeight
screen.colorDepth / pixelDepth
devicePixelRatio
locale / timezone
WebGL vendor / renderer
canvas output perturbation
```

The lab intentionally does **not** forge native function source and intentionally keeps `window.__flipbotFingerprintLab`. `navigator.webdriver` is observed by the detector rather than hidden. This keeps the system useful for defensive consistency research instead of turning it into a production anti-bot bypass layer.

### Detector

The built-in page compares independent signal families, including legacy navigator values, UA Client Hints, screen/DPR, locale/timezone, WebGL, canvas integrity, Worker-vs-window consistency, media-device availability, WebGPU/WebRTC/WebAssembly capability and automation signals.

### First-party cookie and session lifecycle

The built-in loopback server creates a normal first-party `HttpOnly`, `SameSite=Lax` laboratory session cookie. The server records only whether a cookie header is present; it does not echo the cookie value.

Standalone lab sessions may optionally persist cookies/local storage via Playwright storage state. Persistent state is encrypted with AES-256-GCM and stored under:

```text
sessions/fingerprint-lab/<profile>.state.enc
```

Enable persistence with:

```text
FLIPBOT_FINGERPRINT_LAB_PERSIST_SESSION=true
```

It requires either:

```text
FLIPBOT_SESSION_ENCRYPTION_KEY
```

or the fallback:

```text
FLIPBOT_ENCRYPTION_KEY
```

The configured Base64 key must decode to exactly 32 bytes. Persistence is disabled by default.

The guarded `BrowserManager` integration does **not** replace FlipBot's normal per-bot session state with this profile store; regular bot sessions remain authoritative.

### Human-interaction simulation

Enable controlled local/test interaction pacing with:

```text
FLIPBOT_FINGERPRINT_LAB_HUMAN_BEHAVIOR=true
```

The simulator performs bounded mouse travel, pauses, scrolling and optional element interaction. Every operation re-checks the current page URL against `FingerprintLabPolicy`, so it refuses to operate after navigation to a production host.

### Laboratory proxy path

For network-path experiments against your controlled platform you may supply a **local or reserved-test proxy only**:

```text
FLIPBOT_FINGERPRINT_LAB_PROXY_URL=http://127.0.0.1:8888
```

or for example:

```text
FLIPBOT_FINGERPRINT_LAB_PROXY_URL=socks5://proxy.test:1080
```

The proxy endpoint itself must be loopback / `*.localhost` / `.test` / `*.test`. External proxy services are rejected.

## Run standalone on Windows PowerShell

From the repository:

```powershell
cd C:\Users\jedre\Desktop\flipbot\playwright
$env:FLIPBOT_FINGERPRINT_LAB="true"

..\backend\mvnw.cmd -f .\pom.xml test

..\backend\mvnw.cmd -f .\pom.xml -DskipTests compile org.codehaus.mojo:exec-maven-plugin:3.6.3:java `
  "-Dexec.mainClass=pl.flipbot.playwright.lab.fingerprint.FingerprintLabApplication"
```

Without a custom target, the built-in detector starts on:

```text
http://127.0.0.1:18091/
```

## Run against your Vinted-shaped test platform

Use an allowed local/test hostname, for example:

```powershell
$env:FLIPBOT_FINGERPRINT_LAB="true"
$env:FLIPBOT_FINGERPRINT_LAB_URL="http://localhost:5173/fingerprint-test"
$env:FLIPBOT_FINGERPRINT_LAB_PROFILE="windows-laptop-pl"
$env:FLIPBOT_FINGERPRINT_LAB_HUMAN_BEHAVIOR="true"

..\backend\mvnw.cmd -f .\pom.xml -DskipTests compile org.codehaus.mojo:exec-maven-plugin:3.6.3:java `
  "-Dexec.mainClass=pl.flipbot.playwright.lab.fingerprint.FingerprintLabApplication"
```

For encrypted warm-session testing also set:

```powershell
$env:FLIPBOT_FINGERPRINT_LAB_PERSIST_SESSION="true"
```

and provide the normal session/encryption key.

## Guarded integration with BrowserManager

The regular `BrowserManager.createContext(...)` path can use a selected lab profile only when all required gates are set:

```text
FLIPBOT_FINGERPRINT_RUNTIME_INTEGRATION=true
FLIPBOT_FINGERPRINT_LAB=true
FLIPBOT_FINGERPRINT_LAB_URL=<allowed laboratory URL>
```

Optional profile and local-proxy variables are shared with the standalone lab.

When active, BrowserManager applies laboratory context options, blocks Service Workers, installs the HTTP/WebSocket safety boundary and loads the synthetic fingerprint script before documents execute. Normal per-bot session logic remains unchanged.

## Multi-context load / isolation harness

`FingerprintLabLoadApplication` studies many isolated browser contexts on one machine without opening production targets.

The harness keeps all Playwright calls on one thread and processes contexts in bounded batches instead of blindly trying to keep a thousand full pages alive at once.

Configuration:

```text
FLIPBOT_FINGERPRINT_LAB_CONTEXTS=20
FLIPBOT_FINGERPRINT_LAB_BATCH_SIZE=10
```

Hard limits:

```text
contexts: 1..1000
batch size: 1..100
```

Example:

```powershell
$env:FLIPBOT_FINGERPRINT_LAB="true"
$env:FLIPBOT_FINGERPRINT_LAB_URL="http://localhost:5173/fingerprint-test"
$env:FLIPBOT_FINGERPRINT_LAB_CONTEXTS="200"
$env:FLIPBOT_FINGERPRINT_LAB_BATCH_SIZE="20"

..\backend\mvnw.cmd -f .\pom.xml -DskipTests compile org.codehaus.mojo:exec-maven-plugin:3.6.3:java `
  "-Dexec.mainClass=pl.flipbot.playwright.lab.fingerprint.FingerprintLabLoadApplication"
```

The load harness rotates through the profile catalog and prints progress plus JVM heap usage.

## Architecture

Main classes:

```text
FingerprintLabPolicy                 hard URL/WebSocket/proxy boundary
FingerprintLabConfiguration          central environment configuration
FingerprintLabProfileCatalog         coherent named synthetic profiles
FingerprintLabScript                 browser-surface simulator
FingerprintLab                       fingerprint capture
FingerprintLabServer                 loopback detector + first-party cookie
FingerprintLabSessionStore           encrypted warm-session state
FingerprintLabHumanBehavior          local/test-only interaction simulator
FingerprintLabApplication            standalone consolidated launcher
FingerprintLabRuntimeIntegration     guarded BrowserManager bridge
FingerprintLabLoadApplication        bounded multi-context scale harness
```

Keep `FingerprintLabPolicy` and the runtime network boundary intact when extending the laboratory.
