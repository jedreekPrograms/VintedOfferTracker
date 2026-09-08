# FlipBot Mobile

Standalone phone application for FlipBot. The existing desktop application remains in `frontend/` and mobile UI changes do not touch it.

## Run on the PC

```powershell
cd mobile
npm ci
npm run dev
```

The mobile development server listens on `0.0.0.0:5174` and proxies `/api` to the Spring backend on `localhost:8081`.

For Android installation, expose only the mobile server through a private HTTPS endpoint such as Tailscale Serve, then install it from Chrome as a PWA.

The mobile app contains the same operational sections as the desktop panel, plus the phone-specific CAPTCHA queue and hold controller.

See `../docs/mobile-app.md` for the complete setup and CAPTCHA flow.
