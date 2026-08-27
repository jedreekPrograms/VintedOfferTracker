# FlipBot Fingerprint Lab v2

This module is one consolidated **local / reserved-test laboratory** for studying browser fingerprint consistency, session isolation, first-party cookie lifecycle, bounded multi-context scale, network-path observations and human-interaction simulations.

The normal entry point is now deliberately simple: one switch, one controlled URL and one fleet size. Advanced laboratory flags remain available, but are no longer required for the common test workflow.

## Simple controlled runtime

For a Vinted-shaped clone on a local or reserved `.test` hostname, set only:

```text
FLIPBOT_TEST_AUTOMATION=true
FLIPBOT_TEST_URL=http://vinted-clone.test
FLIPBOT_TEST_BOTS=350
```

That activates the existing fingerprint-lab stack for the controlled target and makes the load harness model a fleet of up to 350 isolated browser contexts in bounded batches.

Simple mode automatically enables the human-interaction simulator. If `FLIPBOT_SESSION_ENCRYPTION_KEY` or `FLIPBOT_ENCRYPTION_KEY` already exists, standalone warm-session persistence can be used without another enable flag.

The three-variable mode is intentionally not a production-site switch. `FLIPBOT_TEST_URL` still has to be one of the allowed laboratory host classes below.

## Hard safety boundary

Allowed target hosts are only:

```text
localhost
127.0.0.1
::1
*.localhost
test
*.test
```

HTTP(S) traffic to other hosts is aborted. WebSockets are independently restricted to the same laboratory host classes. Service Workers are blocked.

The boundary is kept in a small number of understandable layers:

1. `ControlledTestRuntime` validates the three-variable configuration.
2. `FingerprintLabPolicy` is the central host/scheme allowlist.
3. `FingerprintLabRuntimeIntegration` refuses to instrument a non-laboratory target.
4. `FingerprintLabApplication.installNetworkSafetyBoundary` aborts escaped HTTP/WebSocket traffic.
5. `FingerprintLabHumanBehavior` re-checks the current page before and after simulated interactions.

This keeps the common configuration simple without turning the target check into a single fragile toggle.

## Consolidated capabilities

### Coherent profile catalog

Available synthetic profiles:

```text
windows-desktop-pl
windows-laptop-pl
windows-desktop-en
```

The fleet harness rotates through those coherent profiles deterministically. Each profile keeps related values together: platform, CPU count, memory, language order, touch points, screen/available screen geometry, DPR, timezone and WebGL identity.

Advanced selection remains available through:

```text
FLIPBOT_FINGERPRINT_LAB_PROFILE
```

### Fingerprint surfaces

The laboratory demonstrates:

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

The lab intentionally remains a detectable simulator: it keeps `window.__flipbotFingerprintLab` and does not forge native function source. This makes it suitable for defensive consistency and detector testing on your own platform.

### Detector and cross-signal consistency

The built-in page compares independent signal families, including navigator values, UA Client Hints, screen/DPR, locale/timezone, WebGL, canvas integrity, Worker-vs-window consistency, media-device availability, WebGPU/WebRTC/WebAssembly capability and automation signals.

This is useful for testing the modern anti-bot idea that a client should be evaluated as a coherent bundle of signals rather than by one boolean such as `navigator.webdriver`.

### Cookies, storage and session lifecycle

The built-in loopback server creates a first-party `HttpOnly`, `SameSite=Lax` laboratory session cookie. BrowserContext isolation keeps cookies/localStorage separated between contexts.

Standalone warm-session state can be encrypted with AES-256-GCM under:

```text
sessions/fingerprint-lab/<profile>.state.enc
```

Advanced persistence flag:

```text
FLIPBOT_FINGERPRINT_LAB_PERSIST_SESSION=true
```

It requires either:

```text
FLIPBOT_SESSION_ENCRYPTION_KEY
```

or:

```text
FLIPBOT_ENCRYPTION_KEY
```

The Base64 key must decode to exactly 32 bytes.

### Human-interaction simulation

Simple controlled mode enables the laboratory mouse/scroll/typing simulator automatically. Advanced mode can enable it with:

```text
FLIPBOT_FINGERPRINT_LAB_HUMAN_BEHAVIOR=true
```

Every operation re-checks the current URL against `FingerprintLabPolicy` before and after browser interaction.

### Laboratory proxy path

Advanced experiments against your controlled platform may supply a **local or reserved-test proxy only**:

```text
FLIPBOT_FINGERPRINT_LAB_PROXY_URL=http://127.0.0.1:8888
```

or:

```text
FLIPBOT_FINGERPRINT_LAB_PROXY_URL=socks5://proxy.test:1080
```

External proxy services are rejected.

## 350-context fleet harness

`FingerprintLabLoadApplication` is the fleet/load entry point.

In simple controlled mode:

- `FLIPBOT_TEST_BOTS` accepts `1..350`;
- default fleet size is `350`;
- contexts are processed in batches of at most `25`;
- one Chromium process is reused;
- each BrowserContext remains storage-isolated;
- profiles are assigned deterministically across the fleet;
- production network escapes are still blocked.

The batches are intentional: 350 logical browser identities do not require 350 full pages to remain live in RAM simultaneously.

PowerShell example:

```powershell
cd C:\Users\jedre\Desktop\flipbot\playwright

$env:FLIPBOT_TEST_AUTOMATION="true"
$env:FLIPBOT_TEST_URL="http://vinted-clone.test"
$env:FLIPBOT_TEST_BOTS="350"

..\backend\mvnw.cmd -f .\pom.xml -DskipTests compile org.codehaus.mojo:exec-maven-plugin:3.6.3:java `
  "-Dexec.mainClass=pl.flipbot.playwright.lab.fingerprint.FingerprintLabLoadApplication"
```

If you do not want to configure a local DNS/hosts entry, use a loopback URL instead, for example:

```text
http://127.0.0.1:5173/fingerprint-test
```

## BrowserManager integration

When `FLIPBOT_TEST_AUTOMATION=true`, the guarded `BrowserManager` bridge is requested automatically and takes its controlled target from `FLIPBOT_TEST_URL`.

The old advanced three-gate form is still supported for compatibility:

```text
FLIPBOT_FINGERPRINT_RUNTIME_INTEGRATION=true
FLIPBOT_FINGERPRINT_LAB=true
FLIPBOT_FINGERPRINT_LAB_URL=<allowed laboratory URL>
```

When active, BrowserManager applies laboratory context options, blocks Service Workers, installs the HTTP/WebSocket safety boundary and loads the synthetic fingerprint script before documents execute. Normal per-bot FlipBot session logic remains authoritative.

## Advanced standalone mode

The original standalone launcher remains available:

```powershell
$env:FLIPBOT_FINGERPRINT_LAB="true"
$env:FLIPBOT_FINGERPRINT_LAB_URL="http://localhost:5173/fingerprint-test"
$env:FLIPBOT_FINGERPRINT_LAB_PROFILE="windows-laptop-pl"
$env:FLIPBOT_FINGERPRINT_LAB_HUMAN_BEHAVIOR="true"

..\backend\mvnw.cmd -f .\pom.xml -DskipTests compile org.codehaus.mojo:exec-maven-plugin:3.6.3:java `
  "-Dexec.mainClass=pl.flipbot.playwright.lab.fingerprint.FingerprintLabApplication"
```

Advanced load controls are also retained:

```text
FLIPBOT_FINGERPRINT_LAB_CONTEXTS=20
FLIPBOT_FINGERPRINT_LAB_BATCH_SIZE=10
```

Their historical hard limits remain `1..1000` contexts and `1..100` contexts per batch. The simple fleet interface deliberately caps itself at 350.

## Architecture

Main classes:

```text
ControlledTestRuntime                 three-variable simple configuration
FingerprintLabPolicy                 central URL/WebSocket/proxy boundary
FingerprintLabConfiguration          advanced + simple environment mapping
FingerprintLabProfileCatalog         coherent named synthetic profiles
FingerprintLabScript                 browser-surface simulator
FingerprintLab                       fingerprint capture
FingerprintLabServer                 loopback detector + first-party cookie
FingerprintLabSessionStore           encrypted warm-session state
FingerprintLabHumanBehavior          local/test-only interaction simulator
FingerprintLabApplication            standalone consolidated launcher
FingerprintLabRuntimeIntegration     guarded BrowserManager bridge
FingerprintLabLoadApplication        bounded fleet/load harness
```

Keep `FingerprintLabPolicy` and the runtime network boundary intact when extending the controlled test runtime.
