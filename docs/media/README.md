# FlipBot demo media

This directory is reserved for public README/demo assets.

Recommended final files:

```text
docs/media/
├── dashboard.png
├── bot-configuration.png
├── live-discovery.png
├── negotiation-state.png
├── market-statistics.png
└── demo-thumbnail.png
```

## Screenshots

Keep screenshots focused and readable:

1. `dashboard.png` — running bots and high-level state.
2. `bot-configuration.png` — target mode, category/model, prices and negotiation steps.
3. `live-discovery.png` — useful scan/filter state; avoid a giant wall of logs.
4. `negotiation-state.png` — persisted conversation/step/waiting state.
5. `market-statistics.png` — model activity / observations.

Recommended width: **1400–1800 px**. Crop browser/IDE chrome when it does not add useful context. Blur or replace private e-mail addresses, passwords, cookies, session values and other account-specific data before committing screenshots.

## Video

The repository should not store a large raw recording unless there is a specific reason to do so. A better setup is:

- upload a polished **60–120 s** demo to YouTube (unlisted is fine), Loom or Vimeo,
- export one strong frame as `demo-thumbnail.png`,
- link that thumbnail from the root `README.md`.

Suggested Markdown after the video is ready:

```md
[![Watch the FlipBot demo](docs/media/demo-thumbnail.png)](YOUR_VIDEO_URL)
```

Use the video to prove end-to-end behavior; use the screenshots so the project is understandable without pressing Play.
