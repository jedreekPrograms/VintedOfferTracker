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
    private static final double MODEL_OPTION_SETTLE_TIMEOUT_MS = 10_000;
    private static final double MODEL_OPTION_POLL_INTERVAL_MS = 250;

    private final Page page;
    private String activeFilterTestId;
    private String activeFilterBaseUrl;
    private String selectedBrandOption;
    private String lastKnownSafeVintedUrl;

    public void openFilter(String filterTestId) {
        ensureVintedBeforeFilterAction("opening filter " + filterTestId);

        if (FilterSelectors.BRAND_FILTER.equals(filterTestId)) {
            activeFilterBaseUrl = page.url();
            selectedBrandOption = null;
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

        String selector = "[data-testid^='selectable-item-brand_collection-']";
        Locator allModelOptions = page.locator(selector);
        long deadline = System.currentTimeMillis() + (long) MODEL_OPTION_SETTLE_TIMEOUT_MS;
        List<String> lastVisiblePartialLabels = List.of();
        int lastVisibleCount = 0;

        while (System.currentTimeMillis() <= deadline) {
            ensureVintedBeforeFilterAction("waiting for exact model '" + model + "'");

            int optionCount = allModelOptions.count();
            List<Locator> exactMatches = new ArrayList<>();
            Set<String> partialLabels = new LinkedHashSet<>();
            int visibleCount = 0;

            for (int index = 0; index < optionCount; index++) {
                Locator candidate = allModelOptions.nth(index);

                if (!safeIsVisible(candidate)) {
                    continue;
                }

                visibleCount++;
                String visibleText = safeInnerText(candidate);

                if (exactVisibleModelLabelMatches(model, visibleText)) {
                    exactMatches.add(candidate);
                    continue;
                }

                String normalizedVisible = normalizeOptionText(visibleText);
                if (containsIgnoreCase(normalizedVisible, normalizeOptionText(model))) {
                    partialLabels.add(normalizedVisible);
                }
            }

            lastVisibleCount = visibleCount;
            lastVisiblePartialLabels = List.copyOf(partialLabels);

            if (!exactMatches.isEmpty()) {
                if (exactMatches.size() > 1) {
                    log.warn(
                            "[FILTER MODEL] Found {} visible exact options for '{}'. Using the first exact option.",
                            exactMatches.size(),
                            model
                    );
                }

                Locator modelLocator = exactMatches.getFirst();
                String actualOptionText = normalizeOptionText(safeInnerText(modelLocator));
                String testId = modelLocator.getAttribute("data-testid");

                log.info(
                        "[FILTER MODEL] Exact visible Vinted model resolved after filtered-list settling. requested='{}', visibleOption='{}', testId='{}'. Partial variants are never accepted.",
                        model,
                        actualOptionText,
                        testId
                );

                modelLocator.click();
                assertStillOnVinted("selecting exact model '" + model + "'");
                return;
            }

            page.waitForTimeout(MODEL_OPTION_POLL_INTERVAL_MS);
        }

        throw new IllegalStateException(
                "Could not find an exact visible Vinted model option for '"
                        + model
                        + "' within "
                        + Math.round(MODEL_OPTION_SETTLE_TIMEOUT_MS / 1_000)
                        + " seconds. Visible model options="
                        + lastVisibleCount
                        + ", visible partial matches="
                        + lastVisiblePartialLabels
                        + ". Refusing to click Edge/Ultra/FE/Plus or any other partial variant."
        );
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

        return visibleText.lines()
                .map(FilterActions::normalizeOptionText)
                .filter(line -> !line.isBlank())
                .anyMatch(line -> normalizedRequested.equalsIgnoreCase(line));
    }

    static String normalizeOptionText(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private boolean containsIgnoreCase(String value, String fragment) {
        if (value == null || fragment == null || fragment.isBlank()) {
            return false;
        }
        return value.toLowerCase().contains(fragment.toLowerCase());
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

    public void clickConfirmButton() {
        ensureVintedBeforeFilterAction("confirming filter selection");
        Locator button = page.getByTestId("filter-selection-button");
        waitUntilVisible(button, OPTION_TIMEOUT_MS);
        button.click();
        assertStillOnVinted("confirming filter selection");
    }

    public void clickOutsideSafely() {
        ensureVintedBeforeFilterAction("closing filter panel");

        /*
         * Escape is a native, low-risk way to dismiss Vinted's filter drawer.
         * The old synthetic document.body MouseEvent script occasionally
         * produced a JavaScript SyntaxError and forced an unnecessary URL
         * fallback even though the entered prices were already valid.
         */
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

    /**
     * Detect an ad/external takeover immediately. Recover the main page before
     * propagating an exception so callers that implement a URL fallback never
     * accidentally append Vinted query parameters to the external URL.
     */
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
