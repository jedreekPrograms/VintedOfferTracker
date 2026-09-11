package pl.flipbot.playwright.browser;

/**
 * Keeps Vinted's OneTrust consent UI from covering catalog controls.
 *
 * The guard is intentionally narrow: it only clicks known OneTrust controls
 * or buttons whose complete visible label is an explicit accept-all action.
 * It never removes arbitrary DOM nodes and never force-clicks through an
 * overlay. If the preference center itself is open and no accept-all control
 * is available, it may use OneTrust's own save/close control to get the
 * blocking preference-center layer out of the way.
 */
final class OneTrustConsentGuard {

    private static final String SCRIPT = """
            (() => {
                const ACCEPT_CONTROL_IDS = [
                    "onetrust-accept-btn-handler",
                    "accept-recommended-btn-handler"
                ];

                const PREFERENCE_CENTER_EXIT_IDS = [
                    "save-preference-btn-handler",
                    "close-pc-btn-handler"
                ];

                const ACCEPT_ALL_LABELS = new Set([
                    "zgoda na wszystkie",
                    "akceptuj wszystkie",
                    "zaakceptuj wszystkie",
                    "zezwól na wszystkie",
                    "zezwol na wszystkie",
                    "pozwól na wszystkie",
                    "pozwol na wszystkie",
                    "accept all",
                    "allow all"
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

                const clickIfUsable = (element) => {
                    if (!(element instanceof HTMLElement)
                            || !isVisible(element)
                            || element.hasAttribute("disabled")) {
                        return false;
                    }

                    element.click();
                    return true;
                };

                const clickKnownId = (id) =>
                    clickIfUsable(document.getElementById(id));

                const clickExplicitAcceptAllLabel = () => {
                    const buttons = Array.from(
                        document.querySelectorAll(
                            "#onetrust-consent-sdk button, #onetrust-pc-sdk button"
                        )
                    );

                    for (const button of buttons) {
                        if (!isVisible(button)
                                || button.hasAttribute("disabled")) {
                            continue;
                        }

                        const label = normalize(
                            button.innerText
                                || button.textContent
                                || button.getAttribute("aria-label")
                                || button.getAttribute("title")
                        );

                        if (ACCEPT_ALL_LABELS.has(label)) {
                            button.click();
                            return true;
                        }
                    }

                    return false;
                };

                const preferenceCenterBlocksPage = () => {
                    const darkFilter = document.querySelector(
                        ".onetrust-pc-dark-filter"
                    );
                    const preferenceCenter = document.querySelector(
                        "#onetrust-pc-sdk"
                    );

                    return isVisible(darkFilter) || isVisible(preferenceCenter);
                };

                const stabilizeOneTrust = () => {
                    for (const id of ACCEPT_CONTROL_IDS) {
                        if (clickKnownId(id)) {
                            return true;
                        }
                    }

                    if (clickExplicitAcceptAllLabel()) {
                        return true;
                    }

                    if (preferenceCenterBlocksPage()) {
                        for (const id of PREFERENCE_CENTER_EXIT_IDS) {
                            if (clickKnownId(id)) {
                                return true;
                            }
                        }
                    }

                    return false;
                };

                let scheduled = false;
                const scheduleStabilization = () => {
                    if (scheduled) {
                        return;
                    }

                    scheduled = true;
                    queueMicrotask(() => {
                        scheduled = false;
                        stabilizeOneTrust();
                    });
                };

                scheduleStabilization();

                const mutationObserver = new MutationObserver(
                    scheduleStabilization
                );
                mutationObserver.observe(document, {
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

                let pollsRemaining = 120;
                const poll = setInterval(() => {
                    stabilizeOneTrust();
                    pollsRemaining--;

                    if (pollsRemaining <= 0) {
                        clearInterval(poll);
                    }
                }, 500);
            })();
            """;

    private OneTrustConsentGuard() {
    }

    static String script() {
        return SCRIPT;
    }
}
