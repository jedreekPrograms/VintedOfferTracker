# FlipBot Mobile

FlipBot Mobile uses the same React frontend and backend as the desktop panel. The computer remains the server and Playwright browser host; the phone is an installable control surface.

## Architecture

- PC: Spring backend, Playwright, database, bot sessions and Chromium.
- Phone: installable PWA served by the PC.
- API: the existing `/api` routes plus transient CAPTCHA-control endpoints.
- Browser session: manual recovery still uses the same `sessions/bot-X.json` file as the affected bot.

## Recommended remote access

Do not expose the Spring backend or Vite development server directly to the public internet. Use a private VPN such as Tailscale between the PC and phone.

For PWA installation on Android, serve the built frontend over HTTPS (for example through Tailscale Serve) so the browser treats it as a secure context. The backend can remain bound to the PC and be reverse-proxied under the same origin as `/api`.

## Development

Run the existing services on the PC:

```powershell
# backend
cd backend
.\mvnw.cmd spring-boot:run

# frontend development server
cd ..\frontend
npm install
npm run dev -- --host 0.0.0.0

# Playwright worker
cd ..\playwright
.\mvnw.cmd exec:java
```

The development Vite proxy continues to forward `/api` to `http://localhost:8081`.

## Production PWA

Build the frontend:

```powershell
cd frontend
npm ci
npm run build
```

Serve `frontend/dist` behind HTTPS and proxy `/api` to the local Spring backend. Open that HTTPS address on Android and choose **Install app** / **Add to Home screen**. FlipBot launches in standalone mode with its own icon and mobile navigation.

## CAPTCHA from the phone

1. A bot detects human verification and enters `CAPTCHA_REQUIRED`.
2. Open **CAPTCHA** in FlipBot Mobile.
3. Select the bot and tap **Rozwiąż CAPTCHA**.
4. The PC opens the headed recovery browser with the affected bot's saved session.
5. Login recovery continues until the real challenge is visible.
6. The mobile control changes to `READY`.
7. Hold **TRZYMAJ →** on the phone.
8. While a fresh hold heartbeat exists, the PC moves the challenge slider in the headed browser.
9. Releasing the button stops the heartbeat and releases the mouse.
10. When human-verification evidence disappears, the refreshed session is saved and normal scheduling resumes.

The pointer movement is fail-safe. Playwright requires a recent mobile heartbeat for every movement tick. If the phone loses connectivity or stops sending heartbeats, the PC releases the mouse rather than continuing the drag.

Local manual completion in the visible Chromium window remains available as a fallback.
