package pl.flipbot.playwright.browser;

final class VintedInformationalDialogGuard {

    private static final String SCRIPT = """
            (() => {
                const ELECTRONICS_OVERLAY_SELECTOR =
                    '[data-testid="electronics-verification-pop-up-dialog--overlay"]';

                const ELECTRONICS_DIALOG_TITLES = [
                    "weryfikacja elektroniki",
                    "electronics verification"
                ];

                const ACKNOWLEDGE_LABELS = new Set([
                    "rozumiem",
                    "got it",
                    "i understand",
                    "understand"
                ]);

                const CLOSE_LABELS = new Set([
                    "zamknij",
                    "close"
                ]);

                const normalize = (value) =>
                    String(value ?? "")
                        .replace(/\\s+/g, " ")
                        .trim()
                        .toLowerCase();

                const isVisible = (element) => {
                    if (!(element instanceof Element)) {
                        return false;
                    }

                    const style = window.getComputedStyle(element);
                    const rect = element.getBoundingClientRect();

                    return style.display !== "none"
                        && style.visibility !== "hidden"
                        && Number(style.opacity || "1") > 0
                        && rect.width > 0
                        && rect.height > 0;
                };

                const controlLabel = (element) => normalize(
                    element.innerText
                        || element.textContent
                        || element.getAttribute("aria-label")
                        || element.getAttribute("title")
                );

                const isExpectedElectronicsDialog = (overlay) => {
                    if (!(overlay instanceof Element) || !isVisible(overlay)) {
                        return false;
                    }

                    const text = normalize(
                        overlay.innerText || overlay.textContent
                    );

                    return ELECTRONICS_DIALOG_TITLES.some(
                        title => text.includes(title)
                    );
                };

                const clickExactButton = (overlay, acceptedLabels) => {
                    const buttons = Array.from(
                        overlay.querySelectorAll("button")
                    );

                    for (const button of buttons) {
                        if (!isVisible(button)
                                || button.hasAttribute("disabled")) {
                            continue;
                        }

                        if (acceptedLabels.has(controlLabel(button))) {
                            button.click();
                            return true;
                        }
                    }

                    return false;
                };

                const dismissElectronicsVerification = () => {
                    const overlays = Array.from(
                        document.querySelectorAll(
                            ELECTRONICS_OVERLAY_SELECTOR
                        )
                    );

                    for (const overlay of overlays) {
                        if (!isExpectedElectronicsDialog(overlay)) {
                            continue;
                        }

                        // Prefer the explicit acknowledgement action visible in
                        // the Vinted dialog (for example Polish "Rozumiem").
                        if (clickExactButton(overlay, ACKNOWLEDGE_LABELS)) {
                            return true;
                        }

                        // Safe fallback: only an explicitly labelled close
                        // button inside this exact electronics-verification
                        // overlay. Never click an arbitrary modal button.
                        if (clickExactButton(overlay, CLOSE_LABELS)) {
                            return true;
                        }
                    }

                    return false;
                };

                let scheduled = false;

                const stabilize = () => {
                    if (scheduled) {
                        return;
                    }

                    scheduled = true;
                    queueMicrotask(() => {
                        scheduled = false;
                        dismissElectronicsVerification();
                    });
                };

                stabilize();

                if (document.readyState === "loading") {
                    document.addEventListener(
                        "DOMContentLoaded",
                        stabilize,
                        { once: true }
                    );
                }

                const observer = new MutationObserver(stabilize);
                observer.observe(document, {
                    childList: true,
                    subtree: true,
                    attributes: true,
                    attributeFilter: [
                        "class",
                        "style",
                        "aria-hidden",
                        "disabled"
                    ]
                });
            })();
            """;

    private VintedInformationalDialogGuard() {
    }

    static String script() {
        return SCRIPT;
    }
}
