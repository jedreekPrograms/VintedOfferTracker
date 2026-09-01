package pl.flipbot.playwright.target;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class VintedSessionBlockDetector {

    private static final List<String> SESSION_BLOCK_MARKERS = List.of(
            "twoja sesja zostala zablokowana",
            "wykrylismy nietypowa lub zautomatyzowana aktywnosc",
            "tymczasowo zablokowalismy ci dostep",
            "your session has been blocked",
            "unusual or automated activity",
            "temporarily blocked your access",
            "access to this site is blocked for this computer"
    );

    public void throwIfBlocked(Page page, String context) {
        if (page == null || page.isClosed()) {
            return;
        }

        try {
            String pageText = safeText(page.title())
                    + "\n"
                    + safeText(page.locator("body").innerText());

            Optional<String> marker = findBlockedMarker(pageText);
            if (marker.isEmpty()) {
                return;
            }

            throw new VintedSessionBlockedException(
                    "Vinted session block detected"
                            + (context == null || context.isBlank() ? "" : " while " + context)
                            + ". Marker: '" + marker.get() + "'. URL: " + safeUrl(page)
            );
        } catch (VintedSessionBlockedException exception) {
            throw exception;
        } catch (PlaywrightException exception) {
            // The page can change while we inspect it. Without positive text
            // evidence we deliberately do not classify the page as blocked.
        }
    }

    static Optional<String> findBlockedMarker(String rawText) {
        String normalized = normalize(rawText);
        if (normalized.isBlank()) {
            return Optional.empty();
        }

        return SESSION_BLOCK_MARKERS.stream()
                .filter(normalized::contains)
                .findFirst();
    }

    private static String normalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String decomposed = Normalizer.normalize(
                text.toLowerCase(Locale.ROOT),
                Normalizer.Form.NFD
        );

        return decomposed
                .replaceAll("\\p{M}+", "")
                .replace('\u00a0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String safeText(String text) {
        return text == null ? "" : text;
    }

    private static String safeUrl(Page page) {
        try {
            return page.url();
        } catch (RuntimeException exception) {
            return "<unavailable>";
        }
    }
}
