package pl.flipbot.playwright.context;

/** Anonymous observer-only consent and model label stabilizer. */
final class AnonymousObserverUiInitScript {
    private AnonymousObserverUiInitScript() { }

    /*
     * The anonymous market observer does not run LoginService, so it used to
     * miss LoginService.acceptCookiesIfVisible(). A late OneTrust dialog could
     * therefore cover the whole catalog and intercept clicks on category,
     * brand and model filters for 30 seconds at a time.
     *
     * The same anonymous UI also exposed a Vinted rendering variant where a
     * model row was visibly labelled (for example "Galaxy A56") but the text
     * was rendered next to the selectable test-id node instead of inside it.
     * FilterActions deliberately refuses to guess model identity, so those
     * rows failed closed even though a human could see the exact option.
     *
     * This observer-only compatibility script fixes both presentation issues:
     * - it accepts the standard OneTrust "accept all" consent as soon as it is
     *   rendered, including after navigation/reset;
     * - for a selectable Vinted model node with no readable label of its own,
     *   it copies text only from the smallest ancestor that contains exactly
     *   one brand_collection id. The text is exposed through title, which the
     *   existing strict Java matcher already reads. It never chooses a model
     *   and never fabricates a collection id.
     */
    static final String ANONYMOUS_OBSERVER_UI_STABILITY_SCRIPT = """
            (() => {
                const MODEL_PREFIX = "selectable-item-brand_collection-";
                const MODEL_SELECTOR = `[data-testid^="${MODEL_PREFIX}"]`;
                // Scan only canonical selectable rows: live DOM has three test IDs
                // per model (row, --title, --suffix). Keep MODEL_SELECTOR for
                // legacy sibling-label provenance checks.
                const MODEL_ROOT_SELECTOR =
                    `[data-testid^="${MODEL_PREFIX}"]:not([data-testid*="--"])`;
                const ACCEPT_ALL_LABELS = new Set([
                    "zgoda na wszystkie",
                    "akceptuj wszystkie",
                    "accept all",
                    "allow all"
                ]);

                const normalize = (value) =>
                    String(value ?? "")
                        .replace(/\\s+/g, " ")
                        .trim();

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

                const acceptCookieConsent = () => {
                    const primary = document.querySelector(
                        "#onetrust-accept-btn-handler"
                    );

                    if (primary instanceof HTMLElement
                            && isVisible(primary)
                            && !primary.hasAttribute("disabled")) {
                        primary.click();
                        return true;
                    }

                    const buttons = Array.from(
                        document.querySelectorAll("button")
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
                        ).toLowerCase();

                        if (ACCEPT_ALL_LABELS.has(label)) {
                            button.click();
                            return true;
                        }
                    }

                    return false;
                };

                const collectionIdFromTestId = (testId) => {
                    const match = /^selectable-item-brand_collection-(\\d+)(?:--title)?$/
                        .exec(String(testId ?? "").trim());
                    return match ? match[1] : null;
                };

                const readableText = (element) => {
                    if (!(element instanceof Element) || !isVisible(element)) {
                        return "";
                    }

                    return normalize(
                        element.innerText
                            || element.textContent
                            || element.getAttribute("aria-label")
                            || element.getAttribute("title")
                    );
                };

                const uniqueCollectionIdsInside = (element) => {
                    const ids = new Set();

                    if (!(element instanceof Element)) {
                        return ids;
                    }

                    const ownId = collectionIdFromTestId(
                        element.getAttribute("data-testid")
                    );
                    if (ownId) {
                        ids.add(ownId);
                    }

                    for (const candidate of element.querySelectorAll(MODEL_SELECTOR)) {
                        const id = collectionIdFromTestId(
                            candidate.getAttribute("data-testid")
                        );
                        if (id) {
                            ids.add(id);
                        }
                    }

                    return ids;
                };

                const looksLikeWholeFilterPanel = (text) => {
                    const lower = normalize(text).toLowerCase();
                    return lower.includes("pokaż wyniki")
                        || lower.includes("pokaz wyniki")
                        || lower.includes("show results")
                        || lower.includes("wyczyść filtry")
                        || lower.includes("wyczysc filtry")
                        || lower.includes("clear filters");
                };

                const resolveAssociatedModelText = (candidate, collectionId) => {
                    const directTitle = document.querySelector(
                        `[data-testid="${MODEL_PREFIX}${collectionId}--title"]`
                    );
                    const directTitleText = readableText(directTitle);
                    if (directTitleText) {
                        return directTitleText;
                    }

                    let current = candidate.parentElement;

                    for (let depth = 0; current && depth < 7; depth++) {
                        const ids = uniqueCollectionIdsInside(current);

                        if (ids.size === 1 && ids.has(collectionId)) {
                            const leafTexts = [];

                            for (const leaf of current.querySelectorAll("*")) {
                                if (leaf.children.length !== 0 || !isVisible(leaf)) {
                                    continue;
                                }

                                const text = readableText(leaf);
                                if (text && !leafTexts.includes(text)) {
                                    leafTexts.push(text);
                                }
                            }

                            const joinedLeaves = normalize(leafTexts.join("\n"));
                            if (joinedLeaves
                                    && joinedLeaves.length <= 220
                                    && !looksLikeWholeFilterPanel(joinedLeaves)) {
                                return joinedLeaves;
                            }

                            const wholeText = readableText(current);
                            if (wholeText
                                    && wholeText.length <= 220
                                    && !looksLikeWholeFilterPanel(wholeText)) {
                                return wholeText;
                            }
                        }

                        current = current.parentElement;
                    }

                    return "";
                };

                const exposeModelLabels = () => {
                    const candidates = Array.from(
                        document.querySelectorAll(MODEL_ROOT_SELECTOR)
                    );

                    for (const candidate of candidates) {
                        const collectionId = collectionIdFromTestId(
                            candidate.getAttribute("data-testid")
                        );
                        if (!collectionId) {
                            continue;
                        }

                        const ownReadableText = normalize(
                            candidate.innerText
                                || candidate.textContent
                                || candidate.getAttribute("aria-label")
                                || candidate.getAttribute("title")
                        );
                        if (ownReadableText) {
                            continue;
                        }

                        const associatedText = resolveAssociatedModelText(
                            candidate,
                            collectionId
                        );

                        if (associatedText) {
                            candidate.setAttribute("title", associatedText);
                        }
                    }
                };

                let scheduled = false;
                const stabilize = () => {
                    if (scheduled) {
                        return;
                    }

                    scheduled = true;
                    queueMicrotask(() => {
                        scheduled = false;
                        acceptCookieConsent();
                        exposeModelLabels();
                    });
                };

                stabilize();

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

}
