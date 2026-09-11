# FlipBot mobile app architecture

## Goals

- Keep the desktop application and every existing route/function available.
- Reuse the same React frontend and Spring/Playwright backend instead of maintaining a second feature set.
- Make the frontend installable as a Progressive Web App on Android.
- Provide a phone-first navigation shell while preserving the desktop sidebar.
- Extend manual CAPTCHA recovery with a remote, user-controlled browser session instead of automating the challenge.

## Existing routes preserved

- `/` Dashboard
- `/runtime` Runtime
- `/bots` Bots
- `/bots/create` Create bot
- `/bots/:botId/edit` Edit/delete bot
- `/action-required` Offers requiring action
- `/history` History
- `/pricing` Model pricing
- `/dictionaries` Dictionaries
- `/dictionaries/manage` Dictionary management

## CAPTCHA target flow

1. A headless bot detects human verification and enters `CAPTCHA_REQUIRED`.
2. The phone shows the paused bot in Runtime/CAPTCHA queue.
3. The user opens manual recovery from the phone.
4. The PC opens the same `bot-X.json` session in a headed Playwright browser and, for authentication challenges, replays the normal login flow until the real challenge is visible.
5. The phone receives a periodically refreshed browser image.
6. The user's pointer/touch gesture is mapped to the Playwright viewport. The server only relays the user's direct interaction; it does not solve or autonomously drag the CAPTCHA.
7. When verification disappears, the same session is saved and the bot resumes its normal headless schedule.

## Connectivity

The frontend continues to call relative `/api` routes. For remote phone use the PC should be reached through a private network such as Tailscale rather than exposing the backend directly to the public Internet.
