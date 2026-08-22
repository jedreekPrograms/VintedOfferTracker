package pl.flipbot.playwright.filters;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.marketplace.MarketplaceUrls;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@RequiredArgsConstructor
public class FilterActions {

    private static final double FILTER_TIMEOUT_MS = 10_000;
    private static final double OPTION_TIMEOUT_MS = 10_000;
    private static final double RELOAD_TIMEOUT_MS = 30_000;

    private static final int BRAND_MAX_ATTEMPTS = 3;
    private static final double BRAND_PRE_CONFIRM_PERSIST_TIMEOUT_MS = 1_000;
    private static final double BRAND_PERSIST_TIMEOUT_MS = 5_000;
    private static final double BRAND_RETRY_DELAY_MS = 2_000;
    private static final double BRAND_PANEL_SETTLE_MS = 400;

    private static final int MODEL_MAX_UI_ATTEMPTS = 3;
    private static final double MODEL_OPTION_SETTLE_TIMEOUT_MS = 10_000;
    private static final double MODEL_OPTION_POLL_INTERVAL_MS = 250;
    private static final double MODEL_PERSIST_TIMEOUT_MS = 5_000;
    private static final double MODEL_RETRY_DELAY_MS = 1_000;
    private static final double MODEL_PANEL_SETTLE_MS = 350;
    private static final String MODEL_TEST_ID_PREFIX =
            "selectable-item-brand_collection-";
    private static final String MODEL_TITLE_TEST_ID_SUFFIX =
            "--title";

    private final Page page;

    private String activeFilterTestId;
    private String activeFilterBaseUrl;
    private String selectedBrandOption;
    private String selectedModelOption;
    private String selectedModelCollectionId;
    private int modelSelectionAttempt;
    private String lastKnownSafeVintedUrl;

    public void openFilter(String filterTestId) {
        ensureVintedBeforeFilterAction("opening filter " + filterTestId);

        if (FilterSelectors.BRAND_FILTER.equals(filterTestId)) {
            activeFilterBaseUrl = page.url();
            selectedBrandOption = null;
        }

        if (FilterSelectors.MODEL_FILTER.equals(filterTestId)) {
            activeFilterBaseUrl = page.url();
            selectedModelOption = null;
            selectedModelCollectionId = null;
            modelSelectionAttempt = 0;
        }

        activeFilterTestId = filterTestId;
        Locator filter = page.getByTestId(filterTestId);
        waitUntilVisible(filter, FILTER_TIMEOUT_MS);
        filter.click();
        assertStillOnVinted("opening filter " + filterTestId);
    }

    public void selectOption(String option) {
        Locator locator = getOptionLocator(option);
        waitUntilVisible(locator, OPTION_TIMEOUT_MS);
        locator.click();

        if (FilterSelectors.BRAND_FILTER.equals(activeFilterTestId)) {
            selectedBrandOption = option;
            return;
        }

        assertStillOnVinted("selecting filter option '" + option + "'");
    }

    public void waitForOption(String option) {
        waitForOption(option, OPTION_TIMEOUT_MS);
    }

    public void waitForOption(String option, double timeoutMilliseconds) {
        waitUntilVisible(getOptionLocator(option), timeoutMilliseconds);
    }

    public void fillInput(String testId, String value) {
        ensureVintedBeforeFilterAction("filling filter input " + testId);
        Locator input = page.getByTestId(testId);
        waitUntilVisible(input, OPTION_TIMEOUT_MS);
        input.fill(value);
    }

    public void pressEnter() {
        ensureVintedBeforeFilterAction("submitting filter input");
        page.keyboard().press("Enter");
    }

    public void clickSelector(String selector) {
        if (FilterSelectors.FILTER_SELECTION.equals(selector)
                && FilterSelectors.BRAND_FILTER.equals(activeFilterTestId)
                && selectedBrandOption != null
                && !selectedBrandOption.isBlank()) {
            try {
                confirmBrandWithRetry(selector);
            } finally {
                clearActiveFilterState();
            }
            return;
        }

        ensureVintedBeforeFilterAction("clicking filter selector " + selector);
        Locator locator = page.locator(selector);
        waitUntilVisible(locator, OPTION_TIMEOUT_MS);
        locator.click();
        assertStillOnVinted("clicking filter selector " + selector);
    }

    private void confirmBrandWithRetry(String confirmSelector) {
        String brand = selectedBrandOption;
        String baseUrl = activeFilterBaseUrl;

        if (!MarketplaceUrls.isCatalogUrl(baseUrl)) {
            throw new IllegalStateException(
                    "Cannot apply brand retry because the pre-brand Vinted catalog URL is missing or unsafe: "
                            + baseUrl
            );
        }

        RuntimeException lastException = null;

        for (int attempt = 1; attempt <= BRAND_MAX_ATTEMPTS; attempt++) {
            try {
                log.info(
                        "[FILTER BRAND] Attempt {}/{} for brand '{}'.",
                        attempt,
                        BRAND_MAX_ATTEMPTS,
                        brand
                );

                if (attempt > 1) {
                    prepareBrandRetry(baseUrl, brand);
                }

                if (waitForBrandFilterPersisted(BRAND_PRE_CONFIRM_PERSIST_TIMEOUT_MS)) {
                    log.info(
                            "[FILTER BRAND] Brand '{}' persisted before confirm on attempt {}/{}. Current URL: {}",
                            brand,
                            attempt,
                            BRAND_MAX_ATTEMPTS,
                            page.url()
                    );
                    return;
                }

                Locator confirmButton = page.locator(confirmSelector);
                waitUntilVisible(confirmButton, OPTION_TIMEOUT_MS);
                confirmButton.click();

                if (waitForBrandFilterPersisted(BRAND_PERSIST_TIMEOUT_MS)) {
                    log.info(
                            "[FILTER BRAND] Brand '{}' persisted successfully on attempt {}/{}. Current URL: {}",
                            brand,
                            attempt,
                            BRAND_MAX_ATTEMPTS,
                            page.url()
                    );
                    return;
                }

                if (attempt < BRAND_MAX_ATTEMPTS) {
                    log.info(
                            "[FILTER BRAND] Attempt {}/{} did not persist '{}' yet. Retrying safely. Current URL: {}",
                            attempt,
                            BRAND_MAX_ATTEMPTS,
                            brand,
                            page.url()
                    );
                } else {
                    log.warn(
                            "[FILTER BRAND] Final attempt {}/{} did not persist '{}'. Current URL: {}",
                            attempt,
                            BRAND_MAX_ATTEMPTS,
                            brand,
                            page.url()
                    );
                }
            } catch (RuntimeException exception) {
                lastException = exception;

                if (attempt < BRAND_MAX_ATTEMPTS) {
                    log.info(
                            "[FILTER BRAND] Attempt {}/{} for '{}' needs retry: {}",
                            attempt,
                            BRAND_MAX_ATTEMPTS,
                            brand,
                            getFriendlyErrorMessage(exception)
                    );
                } else {
                    log.warn(
                            "[FILTER BRAND] Final attempt {}/{} for '{}' failed: {}",
                            attempt,
                            BRAND_MAX_ATTEMPTS,
                            brand,
                            getFriendlyErrorMessage(exception)
                    );
                }

                log.trace(
                        "[FILTER BRAND] Full brand filter error. Attempt {}/{}.",
                        attempt,
                        BRAND_MAX_ATTEMPTS,
                        exception
                );
            }

            if (attempt < BRAND_MAX_ATTEMPTS) {
                resetToBrandBaseUrl(baseUrl, attempt);
                log.info(
                        "[FILTER BRAND] Next attempt in {}ms.",
                        (int) BRAND_RETRY_DELAY_MS
                );
                page.waitForTimeout(BRAND_RETRY_DELAY_MS);
            }
        }

        String message =
                "Could not persist brand filter '"
                        + brand
                        + "' after "
                        + BRAND_MAX_ATTEMPTS
                        + " attempts. Expected either brand_ids[] or canonical /brand/... URL state. Base URL: "
                        + baseUrl
                        + ". Current URL: "
                        + page.url();

        if (lastException != null) {
            throw new IllegalStateException(message, lastException);
        }

        throw new IllegalStateException(message);
    }

    private void prepareBrandRetry(String baseUrl, String brand) {
        if (!isCatalogPage()) {
            navigateToBrandBaseUrl(baseUrl);
        }

        Locator brandFilter = page.getByTestId(FilterSelectors.BRAND_FILTER);
        waitUntilVisible(brandFilter, FILTER_TIMEOUT_MS);
        brandFilter.click();
        assertStillOnVinted("reopening brand filter for retry");
        page.waitForTimeout(BRAND_PANEL_SETTLE_MS);

        Locator brandOption = getOptionLocator(brand);
        waitUntilVisible(brandOption, OPTION_TIMEOUT_MS);
        brandOption.click();

        log.info(
                "[FILTER BRAND] Brand '{}' selected again for retry. Current URL: {}",
                brand,
                page.url()
        );
    }

    private void resetToBrandBaseUrl(String baseUrl, int failedAttempt) {
        log.info(
                "[FILTER BRAND] Resetting catalog to the known-good pre-brand URL after failed attempt {}/{}.",
                failedAttempt,
                BRAND_MAX_ATTEMPTS
        );

        navigateToBrandBaseUrl(baseUrl);

        if (!isCatalogPage()) {
            throw new IllegalStateException(
                    "Brand retry reset did not return to a Vinted catalog page. URL: " + page.url()
            );
        }

        rememberCurrentVintedUrl();
        log.info(
                "[FILTER BRAND] Pre-brand catalog state restored. Current URL: {}",
                page.url()
        );
    }

    private void navigateToBrandBaseUrl(String baseUrl) {
        if (!MarketplaceUrls.isCatalogUrl(baseUrl)) {
            throw new IllegalStateException(
                    "Refusing to use a non-Vinted catalog URL as brand retry base: " + baseUrl
            );
        }
        navigateToSafeVintedUrl(baseUrl);
    }

    public void fillInputBySelector(String selector, Object value) {
        ensureVintedBeforeFilterAction("filling selector " + selector);
        Locator input = page.locator(selector);
        waitUntilVisible(input, OPTION_TIMEOUT_MS);
        input.fill(String.valueOf(value));
    }

    public void clickModel(String model) {
        ensureVintedBeforeFilterAction("selecting model '" + model + "'");

        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("Model cannot be blank");
        }

        selectedModelOption = model;

        String selector = "[data-testid^='" + MODEL_TEST_ID_PREFIX + "']";
        Locator allModelOptions = page.locator(selector);
        long deadline = System.currentTimeMillis() + (long) MODEL_OPTION_SETTLE_TIMEOUT_MS;
        List<String> lastVisiblePartialLabels = List.of();
        int lastVisibleCandidateCount = 0;
        int lastTextMatchedCount = 0;

        while (System.currentTimeMillis() <= deadline) {
            ensureVintedBeforeFilterAction("waiting for exact model '" + model + "'");

            /*
             * hasText() is deliberately only a search/narrowing mechanism.
             * Vinted may split a label into highlighted spans. A child span
             * containing "Galaxy S25" inside a "Galaxy S25 Edge" row is not
             * proof that the full selectable row is the requested model.
             */
            Locator textMatchedOptions = allModelOptions.filter(
                    new Locator.FilterOptions().setHasText(model)
            );
            int textMatchedCount = textMatchedOptions.count();
            Locator candidates = textMatchedCount > 0
                    ? textMatchedOptions
                    : allModelOptions;
            int candidateCount = candidates.count();

            List<Locator> exactMatches = new ArrayList<>();
            Set<String> partialLabels = new LinkedHashSet<>();
            int visibleCandidateCount = 0;

            for (int index = 0; index < candidateCount; index++) {
                Locator candidate = candidates.nth(index);

                if (!safeIsVisible(candidate)) {
                    continue;
                }

                visibleCandidateCount++;
                List<String> optionTexts = readCompleteModelOptionTexts(candidate);

                boolean exact = optionTexts.stream()
                        .anyMatch(text -> exactVisibleModelLabelMatches(model, text));

                if (exact) {
                    exactMatches.add(candidate);
                    continue;
                }

                for (String optionText : optionTexts) {
                    String normalizedVisible = normalizeOptionText(optionText);
                    if (containsIgnoreCase(
                            normalizedVisible,
                            normalizeOptionText(model)
                    )) {
                        partialLabels.add(normalizedVisible);
                    }
                }
            }

            lastVisibleCandidateCount = visibleCandidateCount;
            lastTextMatchedCount = textMatchedCount;
            lastVisiblePartialLabels = List.copyOf(partialLabels);

            if (!exactMatches.isEmpty()) {
                int exactMatchIndex = Math.min(
                        modelSelectionAttempt,
                        exactMatches.size() - 1
                );
                modelSelectionAttempt++;

                if (exactMatches.size() > 1) {
                    log.warn(
                            "[FILTER MODEL] Found {} visible rows that independently prove exact option '{}'. Selection attempt {} will use exact row {}/{}.",
                            exactMatches.size(),
                            model,
                            modelSelectionAttempt,
                            exactMatchIndex + 1,
                            exactMatches.size()
                    );
                }

                Locator modelLocator = exactMatches.get(exactMatchIndex);
                String evidence = readCompleteModelOptionTexts(modelLocator)
                        .stream()
                        .filter(text -> exactVisibleModelLabelMatches(model, text))
                        .map(FilterActions::normalizeOptionText)
                        .findFirst()
                        .orElse(model);
                String testId = modelLocator.getAttribute("data-testid");
                String collectionId = modelCollectionIdFromTestId(testId);

                if (collectionId == null) {
                    throw new IllegalStateException(
                            "Exact model row for '"
                                    + model
                                    + "' has an unexpected data-testid='"
                                    + testId
                                    + "'. Refusing to click because the persisted Vinted collection id could not be verified afterwards."
                    );
                }

                selectedModelCollectionId = collectionId;

                log.info(
                        "[FILTER MODEL] EXACT Vinted model row verified. requested='{}', testId='{}', expectedCollectionId='{}', evidence='{}'. Partial/highlight fragments and variants are rejected.",
                        model,
                        testId,
                        collectionId,
                        evidence
                );

                modelLocator.click();
                assertStillOnVinted("selecting exact model '" + model + "'");
                return;
            }

            page.waitForTimeout(MODEL_OPTION_POLL_INTERVAL_MS);
        }

        throw new IllegalStateException(
                "Could not prove an exact visible Vinted model option for '"
                        + model
                        + "' within "
                        + Math.round(MODEL_OPTION_SETTLE_TIMEOUT_MS / 1_000)
                        + " seconds. hasText candidates="
                        + lastTextMatchedCount
                        + ", visible candidates inspected="
                        + lastVisibleCandidateCount
                        + ", visible partial/full labels="
                        + lastVisiblePartialLabels
                        + ". Failing closed instead of clicking a similar model."
        );
    }

    static String modelCollectionIdFromTestId(String testId) {
        if (testId == null || !testId.startsWith(MODEL_TEST_ID_PREFIX)) {
            return null;
        }

        String candidate = testId.substring(MODEL_TEST_ID_PREFIX.length()).trim();

        if (candidate.endsWith(MODEL_TITLE_TEST_ID_SUFFIX)) {
            candidate = candidate.substring(
                    0,
                    candidate.length() - MODEL_TITLE_TEST_ID_SUFFIX.length()
            );
        }

        if (!candidate.matches("^\\d+$")) {
            return null;
        }

        return candidate;
    }

    static Pattern exactModelOptionPattern(String model) {
        String normalizedModel = normalizeOptionText(model);
        return Pattern.compile(
                "^\\s*" + Pattern.quote(normalizedModel) + "\\s*$",
                Pattern.CASE_INSENSITIVE
        );
    }

    static boolean exactVisibleModelLabelMatches(
            String requestedModel,
            String visibleText
    ) {
        String normalizedRequested = normalizeOptionText(requestedModel);

        if (normalizedRequested.isBlank() || visibleText == null) {
            return false;
        }

        String normalizedVisible = normalizeOptionText(visibleText);
        if (normalizedRequested.equalsIgnoreCase(normalizedVisible)) {
            return true;
        }

        if (startsWithIgnoreCase(normalizedVisible, normalizedRequested)) {
            String suffix = normalizedVisible
                    .substring(normalizedRequested.length())
                    .trim();
            if (isModelOptionMetadata(suffix)) {
                return true;
            }
        }

        List<String> lines = visibleText.lines()
                .map(FilterActions::normalizeOptionText)
                .filter(line -> !line.isBlank())
                .toList();

        for (int index = 0; index < lines.size(); index++) {
            if (!normalizedRequested.equalsIgnoreCase(lines.get(index))) {
                continue;
            }

            boolean onlyMetadataAroundExactLabel = true;
            for (int otherIndex = 0; otherIndex < lines.size(); otherIndex++) {
                if (otherIndex == index) {
                    continue;
                }

                if (!isModelOptionMetadata(lines.get(otherIndex))) {
                    onlyMetadataAroundExactLabel = false;
                    break;
                }
            }

            if (onlyMetadataAroundExactLabel) {
                return true;
            }
        }

        return false;
    }

    private static boolean startsWithIgnoreCase(String value, String prefix) {
        return value.length() >= prefix.length()
                && value.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    private static boolean isModelOptionMetadata(String value) {
        String normalized = normalizeOptionText(value);
        if (normalized.isBlank()) {
            return false;
        }

        if (normalized.matches("^\\d[\\d\\s.,]*$")) {
            return true;
        }

        String lower = normalized.toLowerCase(Locale.ROOT);
        return lower.matches(
                "^\\d[\\d\\s.,]*\\s*(przedmiot\\p{L}*|item\\p{L}*|article\\p{L}*|result\\p{L}*|wynik\\p{L}*)$"
        );
    }

    static String normalizeOptionText(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private List<String> readCompleteModelOptionTexts(Locator locator) {
        Set<String> texts = new LinkedHashSet<>();

        addModelOptionText(texts, safeInnerText(locator));
        addModelOptionText(texts, safeTextContent(locator));
        addModelOptionText(texts, safeAttribute(locator, "aria-label"));
        addModelOptionText(texts, safeAttribute(locator, "title"));

        Locator labels = locator.locator("label");
        int labelCount;

        try {
            labelCount = Math.min(labels.count(), 10);
        } catch (RuntimeException exception) {
            labelCount = 0;
        }

        for (int index = 0; index < labelCount; index++) {
            Locator label = labels.nth(index);

            if (!safeIsVisible(label)) {
                continue;
            }

            addModelOptionText(texts, safeInnerText(label));
            addModelOptionText(texts, safeTextContent(label));
            addModelOptionText(texts, safeAttribute(label, "aria-label"));
            addModelOptionText(texts, safeAttribute(label, "title"));
        }

        return List.copyOf(texts);
    }

    private void addModelOptionText(Set<String> texts, String value) {
        if (value == null || normalizeOptionText(value).isBlank()) {
            return;
        }
        texts.add(value);
    }

    private boolean containsIgnoreCase(String value, String fragment) {
        if (value == null || fragment == null || fragment.isBlank()) {
            return false;
        }
        return value.toLowerCase(Locale.ROOT)
                .contains(fragment.toLowerCase(Locale.ROOT));
    }

    private boolean safeIsVisible(Locator locator) {
        try {
            return locator.isVisible();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private String safeInnerText(Locator locator) {
        try {
            return locator.innerText();
        } catch (RuntimeException exception) {
            return "";
        }
    }

    private String safeTextContent(Locator locator) {
        try {
            String text = locator.textContent();
            return text == null ? "" : text;
        } catch (RuntimeException exception) {
            return "";
        }
    }

    private String safeAttribute(Locator locator, String attributeName) {
        try {
            String value = locator.getAttribute(attributeName);
            return value == null ? "" : value;
        } catch (RuntimeException exception) {
            return "";
        }
    }

    public void clickConfirmButton() {
        ensureVintedBeforeFilterAction("confirming filter selection");

        if (FilterSelectors.MODEL_FILTER.equals(activeFilterTestId)) {
            try {
                confirmExactModelWithRetry();
            } finally {
                clearActiveFilterState();
            }
            return;
        }

        Locator button = page.getByTestId("filter-selection-button");
        waitUntilVisible(button, OPTION_TIMEOUT_MS);
        button.click();
        assertStillOnVinted("confirming filter selection");
        clearActiveFilterState();
    }

    private void confirmExactModelWithRetry() {
        String model = selectedModelOption;
        String expectedModelCollectionId = selectedModelCollectionId;
        String baseUrl = activeFilterBaseUrl;

        if (model == null || model.isBlank()) {
            throw new IllegalStateException(
                    "Model filter confirmation was requested without the exact requested model label."
            );
        }

        if (expectedModelCollectionId == null
                || expectedModelCollectionId.isBlank()) {
            throw new IllegalStateException(
                    "Model filter confirmation was requested without a verified exact model collection id."
            );
        }

        if (!MarketplaceUrls.isCatalogUrl(baseUrl)) {
            throw new IllegalStateException(
                    "Cannot retry exact model filter because the pre-model Vinted catalog URL is missing or unsafe: "
                            + baseUrl
            );
        }

        RuntimeException lastException = null;

        for (int attempt = 1; attempt <= MODEL_MAX_UI_ATTEMPTS; attempt++) {
            try {
                if (attempt > 1) {
                    prepareExactModelRetry(
                            baseUrl,
                            model,
                            expectedModelCollectionId,
                            attempt
                    );
                }

                log.info(
                        "[FILTER MODEL] Confirm attempt {}/{} for exact model '{}' / collectionId={}.",
                        attempt,
                        MODEL_MAX_UI_ATTEMPTS,
                        model,
                        expectedModelCollectionId
                );

                Locator button = page.getByTestId("filter-selection-button");
                waitUntilVisible(button, OPTION_TIMEOUT_MS);
                button.click();
                assertStillOnVinted("confirming exact model selection");

                if (waitForUrlParameterValue(
                        "brand_collection_ids[]",
                        expectedModelCollectionId,
                        MODEL_PERSIST_TIMEOUT_MS
                )) {
                    log.info(
                            "[FILTER MODEL] EXACT model persistence verified end-to-end on attempt {}/{}. brand_collection_ids[]={}. Current URL: {}",
                            attempt,
                            MODEL_MAX_UI_ATTEMPTS,
                            expectedModelCollectionId,
                            page.url()
                    );
                    return;
                }

                log.warn(
                        "[FILTER MODEL] Exact row was proven and clicked, but Vinted dropped collectionId={} after confirm attempt {}/{}. Current URL: {}. Retrying without changing target identity.",
                        expectedModelCollectionId,
                        attempt,
                        MODEL_MAX_UI_ATTEMPTS,
                        page.url()
                );
            } catch (RuntimeException exception) {
                lastException = exception;
                log.warn(
                        "[FILTER MODEL] Exact model confirm attempt {}/{} needs retry. model='{}', collectionId={}, reason={}",
                        attempt,
                        MODEL_MAX_UI_ATTEMPTS,
                        model,
                        expectedModelCollectionId,
                        getFriendlyErrorMessage(exception)
                );
                log.trace(
                        "[FILTER MODEL] Full exact-model retry error. Attempt {}/{}.",
                        attempt,
                        MODEL_MAX_UI_ATTEMPTS,
                        exception
                );
            }

            if (attempt < MODEL_MAX_UI_ATTEMPTS) {
                resetToModelBaseUrl(baseUrl, attempt);
                page.waitForTimeout(MODEL_RETRY_DELAY_MS);
            }
        }

        /*
         * The collection id below is not guessed and does not come from text
         * search. It was parsed from the data-testid of a selectable row whose
         * COMPLETE visible label independently proved the exact requested
         * model. If Vinted's confirmation UI repeatedly loses that state, the
         * same proven id can safely be restored in the catalog URL. We still
         * verify the final URL and fail closed if it does not persist.
         */
        String recoveryUrl = appendExactModelCollectionId(
                baseUrl,
                expectedModelCollectionId
        );

        log.warn(
                "[FILTER MODEL] Vinted UI failed to persist exact model '{}' after {} attempts. Applying fail-safe exact-ID URL recovery using previously proven collectionId={}: {}",
                model,
                MODEL_MAX_UI_ATTEMPTS,
                expectedModelCollectionId,
                recoveryUrl
        );

        try {
            navigateToSafeVintedUrl(recoveryUrl);

            if (waitForUrlParameterValue(
                    "brand_collection_ids[]",
                    expectedModelCollectionId,
                    MODEL_PERSIST_TIMEOUT_MS
            )) {
                log.info(
                        "[FILTER MODEL] EXACT model persistence recovered with the previously proven collection id. brand_collection_ids[]={}. Current URL: {}",
                        expectedModelCollectionId,
                        page.url()
                );
                return;
            }
        } catch (RuntimeException recoveryFailure) {
            if (lastException != null) {
                recoveryFailure.addSuppressed(lastException);
            }
            throw recoveryFailure;
        }

        String message =
                "Vinted did not persist the exact proven model collection id after "
                        + MODEL_MAX_UI_ATTEMPTS
                        + " UI attempts and exact-ID recovery. Model='"
                        + model
                        + "', expected brand_collection_ids[]="
                        + expectedModelCollectionId
                        + ", actual="
                        + getUrlParameter("brand_collection_ids[]")
                        + ", URL="
                        + page.url();

        if (lastException != null) {
            throw new IllegalStateException(message, lastException);
        }

        throw new IllegalStateException(message);
    }

    private void prepareExactModelRetry(
            String baseUrl,
            String model,
            String expectedModelCollectionId,
            int attempt
    ) {
        if (!MarketplaceUrls.isCatalogUrl(page.url())) {
            navigateToSafeVintedUrl(baseUrl);
        }

        Locator modelFilter = page.getByTestId(FilterSelectors.MODEL_FILTER);
        waitUntilVisible(modelFilter, FILTER_TIMEOUT_MS);
        modelFilter.click();
        assertStillOnVinted("reopening model filter for retry");
        page.waitForTimeout(MODEL_PANEL_SETTLE_MS);

        Locator input = page.locator(FilterSelectors.MODEL_SEARCH_INPUT);
        waitUntilVisible(input, OPTION_TIMEOUT_MS);
        input.fill(model);
        page.waitForTimeout(MODEL_PANEL_SETTLE_MS);

        clickModel(model);

        if (!expectedModelCollectionId.equals(selectedModelCollectionId)) {
            throw new IllegalStateException(
                    "Exact model retry resolved a different Vinted collection id. Expected "
                            + expectedModelCollectionId
                            + ", actual "
                            + selectedModelCollectionId
                            + ". Failing closed."
            );
        }

        log.info(
                "[FILTER MODEL] Exact model '{}' selected again for persistence retry {}/{}. collectionId={}.",
                model,
                attempt,
                MODEL_MAX_UI_ATTEMPTS,
                expectedModelCollectionId
        );
    }

    private void resetToModelBaseUrl(String baseUrl, int failedAttempt) {
        log.info(
                "[FILTER MODEL] Resetting catalog to the known-good pre-model URL after failed confirm attempt {}/{}.",
                failedAttempt,
                MODEL_MAX_UI_ATTEMPTS
        );

        navigateToSafeVintedUrl(baseUrl);

        if (!isCatalogPage()) {
            throw new IllegalStateException(
                    "Model retry reset did not return to a Vinted catalog page. URL: "
                            + page.url()
            );
        }

        rememberCurrentVintedUrl();
    }

    private String appendExactModelCollectionId(
            String baseUrl,
            String expectedModelCollectionId
    ) {
        if (!MarketplaceUrls.isCatalogUrl(baseUrl)) {
            throw new IllegalArgumentException(
                    "Refusing exact model URL recovery on a non-catalog URL: " + baseUrl
            );
        }

        String withoutFragment = baseUrl;
        String fragment = "";
        int fragmentIndex = baseUrl.indexOf('#');

        if (fragmentIndex >= 0) {
            withoutFragment = baseUrl.substring(0, fragmentIndex);
            fragment = baseUrl.substring(fragmentIndex);
        }

        String separator = withoutFragment.contains("?") ? "&" : "?";
        return withoutFragment
                + separator
                + "brand_collection_ids[]="
                + expectedModelCollectionId
                + fragment;
    }

    public void clickOutsideSafely() {
        ensureVintedBeforeFilterAction("closing filter panel");
        page.keyboard().press("Escape");
        page.waitForTimeout(400);
        assertStillOnVinted("closing filter panel");
    }

    public void reloadCurrentPage() {
        String currentUrl = page.url();

        if (!MarketplaceUrls.isVintedUrl(currentUrl)) {
            String recoveryUrl = safeRecoveryUrl();
            log.warn(
                    "[PAGE SAFETY] Refusing to reload external main-page URL '{}'. Recovering to '{}'.",
                    currentUrl,
                    recoveryUrl
            );
            navigateToSafeVintedUrl(recoveryUrl);
            return;
        }

        rememberCurrentVintedUrl();
        String recoveryUrl = safeRecoveryUrl();

        page.reload(
                new Page.ReloadOptions()
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(RELOAD_TIMEOUT_MS)
        );

        if (!MarketplaceUrls.isVintedUrl(page.url())) {
            log.warn(
                    "[PAGE SAFETY] Reload left Vinted and reached '{}'. Recovering to '{}'.",
                    page.url(),
                    recoveryUrl
            );
            navigateToSafeVintedUrl(recoveryUrl);
        } else {
            rememberCurrentVintedUrl();
        }
    }

    public void waitForTimeout(double milliseconds) {
        page.waitForTimeout(milliseconds);
    }

    public boolean waitForUrlParameterPresent(
            String parameterName,
            double timeoutMilliseconds
    ) {
        long deadline = System.currentTimeMillis() + (long) timeoutMilliseconds;

        while (System.currentTimeMillis() <= deadline) {
            assertStillOnVinted("waiting for URL parameter '" + parameterName + "'");

            if (getUrlParameter(parameterName) != null) {
                rememberCurrentVintedUrl();
                return true;
            }

            page.waitForTimeout(200);
        }

        return false;
    }

    private boolean waitForUrlParameterValue(
            String parameterName,
            String expectedValue,
            double timeoutMilliseconds
    ) {
        long deadline = System.currentTimeMillis() + (long) timeoutMilliseconds;

        while (System.currentTimeMillis() <= deadline) {
            assertStillOnVinted(
                    "waiting for exact URL parameter '" + parameterName + "'"
            );

            if (expectedValue.equals(getUrlParameter(parameterName))) {
                rememberCurrentVintedUrl();
                return true;
            }

            page.waitForTimeout(200);
        }

        return false;
    }

    public boolean waitForBrandFilterPersisted(double timeoutMilliseconds) {
        long deadline = System.currentTimeMillis() + (long) timeoutMilliseconds;

        while (System.currentTimeMillis() <= deadline) {
            assertStillOnVinted("waiting for brand filter persistence");

            if (hasBrandFilterInUrl()) {
                rememberCurrentVintedUrl();
                return true;
            }

            page.waitForTimeout(200);
        }

        return false;
    }

    public boolean hasBrandFilterInUrl() {
        if (!MarketplaceUrls.isVintedUrl(page.url())) {
            return false;
        }

        if (getUrlParameter("brand_ids[]") != null) {
            return true;
        }

        String currentUrl = page.url();
        int queryIndex = currentUrl.indexOf('?');
        String withoutQuery = queryIndex >= 0
                ? currentUrl.substring(0, queryIndex)
                : currentUrl;
        int fragmentIndex = withoutQuery.indexOf('#');

        if (fragmentIndex >= 0) {
            withoutQuery = withoutQuery.substring(0, fragmentIndex);
        }

        return withoutQuery.contains("/brand/");
    }

    private boolean isCatalogPage() {
        return MarketplaceUrls.isCatalogUrl(page.url());
    }

    private String getUrlParameter(String parameterName) {
        String currentUrl = page.url();
        int questionMarkIndex = currentUrl.indexOf('?');

        if (questionMarkIndex < 0 || questionMarkIndex == currentUrl.length() - 1) {
            return null;
        }

        String query = currentUrl.substring(questionMarkIndex + 1);
        int fragmentIndex = query.indexOf('#');
        if (fragmentIndex >= 0) {
            query = query.substring(0, fragmentIndex);
        }

        for (String parameter : query.split("&")) {
            int equalsIndex = parameter.indexOf('=');
            String rawName = equalsIndex >= 0
                    ? parameter.substring(0, equalsIndex)
                    : parameter;
            String rawValue = equalsIndex >= 0
                    ? parameter.substring(equalsIndex + 1)
                    : "";
            String decodedName = URLDecoder.decode(rawName, StandardCharsets.UTF_8);

            if (parameterName.equals(decodedName)) {
                return URLDecoder.decode(rawValue, StandardCharsets.UTF_8);
            }
        }

        return null;
    }

    private Locator getOptionLocator(String option) {
        return page.getByRole(
                AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName(option)
        );
    }

    private void waitUntilVisible(Locator locator, double timeoutMilliseconds) {
        locator.waitFor(
                new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(timeoutMilliseconds)
        );
    }

    private void ensureVintedBeforeFilterAction(String action) {
        if (MarketplaceUrls.isVintedUrl(page.url())) {
            rememberCurrentVintedUrl();
            return;
        }

        String externalUrl = page.url();
        String recoveryUrl = safeRecoveryUrl();
        log.warn(
                "[PAGE SAFETY] Main page is outside Vinted before {}. URL='{}'. Recovering to '{}'.",
                action,
                externalUrl,
                recoveryUrl
        );
        navigateToSafeVintedUrl(recoveryUrl);
    }

    private void assertStillOnVinted(String action) {
        String currentUrl = page.url();

        if (MarketplaceUrls.isVintedUrl(currentUrl)) {
            rememberCurrentVintedUrl();
            return;
        }

        String recoveryUrl = safeRecoveryUrl();
        log.warn(
                "[PAGE SAFETY] Main page left Vinted while {}. External URL='{}'. Recovering to '{}'.",
                action,
                currentUrl,
                recoveryUrl
        );

        try {
            navigateToSafeVintedUrl(recoveryUrl);
        } catch (RuntimeException recoveryFailure) {
            throw new IllegalStateException(
                    "[PAGE SAFETY] Main page left Vinted while "
                            + action
                            + ". External URL: "
                            + currentUrl
                            + ". Recovery also failed.",
                    recoveryFailure
            );
        }

        throw new IllegalStateException(
                "[PAGE SAFETY] Main page left Vinted while "
                        + action
                        + ". External URL: "
                        + currentUrl
                        + ". Recovered to: "
                        + page.url()
        );
    }

    private String safeRecoveryUrl() {
        if (MarketplaceUrls.isVintedUrl(lastKnownSafeVintedUrl)) {
            return lastKnownSafeVintedUrl;
        }
        if (MarketplaceUrls.isVintedUrl(activeFilterBaseUrl)) {
            return activeFilterBaseUrl;
        }
        return MarketplaceUrls.CATALOG;
    }

    private void navigateToSafeVintedUrl(String url) {
        if (!MarketplaceUrls.isVintedUrl(url)) {
            throw new IllegalArgumentException(
                    "Refusing to navigate filter recovery to non-Vinted URL: " + url
            );
        }

        page.navigate(
                url,
                new Page.NavigateOptions()
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(RELOAD_TIMEOUT_MS)
        );

        if (!MarketplaceUrls.isVintedUrl(page.url())) {
            throw new IllegalStateException(
                    "Vinted recovery navigation ended on an external URL: " + page.url()
            );
        }

        rememberCurrentVintedUrl();
    }

    private void rememberCurrentVintedUrl() {
        String currentUrl = page.url();
        if (MarketplaceUrls.isVintedUrl(currentUrl)) {
            lastKnownSafeVintedUrl = currentUrl;
        }
    }

    private void clearActiveFilterState() {
        activeFilterTestId = null;
        activeFilterBaseUrl = null;
        selectedBrandOption = null;
        selectedModelOption = null;
        selectedModelCollectionId = null;
        modelSelectionAttempt = 0;
    }

    private String getFriendlyErrorMessage(Throwable exception) {
        if (exception == null) {
            return "Unknown error";
        }

        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }

        int firstLineEnd = message.indexOf('\n');
        return firstLineEnd > 0
                ? message.substring(0, firstLineEnd).trim()
                : message.trim();
    }
}
