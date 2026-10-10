package pl.flipbot.playwright.filters;

import com.microsoft.playwright.Locator;

/**
 * Read-only, failure-tolerant DOM probes used during native model discovery.
 * A failed probe gives no selection proof.
 */
final class VintedLocatorSafety {
    private VintedLocatorSafety() {
    }

    static int safeCount(Locator locator) {
        try {
            return locator.count();
        } catch (RuntimeException exception) {
            return 0;
        }
    }

    static boolean safeIsVisible(Locator locator) {
        try {
            return locator.isVisible();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    static String safeAttribute(Locator locator, String attributeName) {
        try {
            String value = locator.getAttribute(attributeName);
            return value == null ? "" : value;
        } catch (RuntimeException exception) {
            return "";
        }
    }

    static String getFriendlyErrorMessage(Throwable exception) {
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
