# FlipBot demo media

This directory is reserved for lightweight public assets used by the root project README.

## Full demo

The polished FlipBot walkthrough is published on YouTube:

**https://www.youtube.com/watch?v=xaNDLMsuKKk**

The full recording is approximately **2:34, 2560×1440 / 60 FPS** and demonstrates the real application rather than a mock-up. The video intentionally stays outside Git history so the repository remains lightweight.

The root README uses YouTube's own thumbnail endpoint as the clickable preview:

```md
[![Watch the full FlipBot demo](https://img.youtube.com/vi/xaNDLMsuKKk/maxresdefault.jpg)](https://www.youtube.com/watch?v=xaNDLMsuKKk)
```

## Demo storyboard

| Approx. time | Scene |
| --- | --- |
| 0:05 | dashboard / operational metrics |
| 0:25 | bot creation and configuration |
| 0:50 | history / persisted results |
| 1:15 | transition to the live automation engine |
| 1:40 | ten headless browser slots / parallel runtime |
| 2:05 | Runtime dashboard and worker state |
| 2:28 | dashboard state changing alongside logs |

## Future screenshots

A few static screenshots can still help readers who do not play the video. Good candidates are:

```text
docs/media/
├── dashboard.png
├── bot-configuration.png
├── negotiation-state.png
└── runtime.png
```

Keep screenshots focused and readable. Crop irrelevant browser/IDE chrome and remove or replace private e-mail addresses, passwords, cookies, session values and other account-specific data before committing them.
