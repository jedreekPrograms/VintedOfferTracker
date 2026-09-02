# FlipBot demo media

This directory contains lightweight public assets used by the root project README.

## Current asset

- `demo-preview.svg` — repository-friendly demo card shown near the top of the README.

The full source recording is intentionally **not** stored in Git history. The current polished walkthrough is approximately **2:34, 2560×1440 / 60 FPS** and is better published on YouTube.

## Recommended final video setup

1. Upload the full recording to **YouTube** (public or unlisted).
2. Keep the original high-quality render outside the Git repository.
3. In the root `README.md`, replace the `YOUTUBE_VIDEO_ID` example with the real ID:

```md
[![Watch the full FlipBot demo](https://img.youtube.com/vi/YOUTUBE_VIDEO_ID/maxresdefault.jpg)](https://youtu.be/YOUTUBE_VIDEO_ID)
```

This gives GitHub a real visual thumbnail while the full-resolution video is streamed by YouTube.

## Demo storyboard

The current recording covers:

| Approx. time | Scene |
| --- | --- |
| 0:05 | dashboard / operational metrics |
| 0:25 | bot creation and configuration |
| 0:50 | history / persisted results |
| 1:15 | live automation engine transition |
| 1:40 | ten headless browser slots |
| 2:05 | Runtime dashboard and worker state |
| 2:28 | dashboard state updating alongside logs |

## Future screenshots

A few static screenshots can still be useful for readers who do not play the video. Good candidates are:

```text
docs/media/
├── demo-preview.svg
├── dashboard.png
├── bot-configuration.png
├── negotiation-state.png
└── runtime.png
```

Keep screenshots focused and readable. Crop irrelevant browser/IDE chrome and remove or replace private e-mail addresses, passwords, cookies, session values and other account-specific data before committing them.
