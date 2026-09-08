# FlipBot Mobile

FlipBot Mobile is a separate phone application kept in `mobile/`. The existing desktop application remains in `frontend/` and is intentionally unchanged.

The computer remains the server and Playwright browser host; the phone is only the installable control surface.

## Architecture

- `frontend/`: existing desktop panel, unchanged.
- `mobile/`: separate installable React PWA designed for phones.
- PC: Spring backend, Playwright, database, bot sessions and Chromium.
- Phone: FlipBot Mobile served by the PC over a private connection.
- API: the same backend `/api` routes used by the desktop panel plus transient CAPTCHA-control endpoints.
- Browser session: manual recovery still uses the affected bot's `sessions/bot-X.json` on the PC.

The mobile application exposes the same operational areas as the desktop UI: Dashboard, Runtime, Bots, bot creation/editing, Action Required, History, pricing/model configuration and dictionaries. CAPTCHA is the mobile-specific workflow.

## Local development

Run the existing services on the PC:

```powershell
# backend
cd backend
.\mvnw.cmd spring-boot:run

# unchanged desktop frontend
cd ..\frontend
npm ci
npm run dev

# separate phone app (port 5174)
cd ..\mobile
npm ci
npm run dev

# Playwright worker
cd ..\playwright
.\mvnw.cmd exec:java
```

The mobile Vite server binds to `0.0.0.0:5174` and proxies `/api` to `http://localhost:8081`. The desktop Vite configuration stays exactly as it was before the mobile application was introduced.

## Recommended phone access

Do not expose Spring or Vite directly to the public internet. Connect the PC and Android phone through Tailscale.

For installation as a PWA Android needs a secure origin. A convenient setup is to expose only the local mobile server through Tailscale Serve, for example:

```powershell
tailscale serve --bg http://127.0.0.1:5174
```

Open the HTTPS Tailscale address on the phone and choose **Install app** / **Add to Home screen**. The application launches in standalone portrait mode with its own FlipBot icon.

For a production build:

```powershell
cd mobile
npm ci
npm run build
npm run preview
```

`npm run preview` uses port `4174`; if using that build, point Tailscale Serve at `http://127.0.0.1:4174` instead.

## CAPTCHA from the phone

1. A bot detects human verification and enters `CAPTCHA_REQUIRED`.
2. Open **CAPTCHA** in FlipBot Mobile.
3. Select the bot and tap **Rozwiąż CAPTCHA**.
4. The PC opens headed Chromium using that bot's saved `bot-X.json` session.
5. If the challenge originated from login, the normal login flow is replayed until the real challenge is visible.
6. The phone control changes to `READY`.
7. Hold **TRZYMAJ →** on the phone.
8. While a fresh hold heartbeat exists, the PC moves the slider in the already-open headed browser.
9. Releasing the button stops the heartbeat and releases the mouse.
10. When human-verification evidence disappears, the session is saved and normal scheduling resumes.

The hold is fail-safe. Playwright requires a recent phone heartbeat for each movement tick. If connectivity disappears or the application stops sending heartbeats, the PC releases the pointer instead of continuing the drag.

Local manual completion in the visible Chromium window remains available as a fallback.
