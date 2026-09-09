package pl.flipbot.playwright.marketstats;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.context.BotContext;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
final class MarketListingPublishedAtResolver {

    private static final ZoneId MARKET_ZONE = ZoneId.of("Europe/Warsaw");
    private static final String VINTED_BASE_URL = "https://www.vinted.pl";

    private static final Pattern RELATIVE_PATTERN = Pattern.compile(
            "(?iu)(?:dodane|dodano|wystawione|uploaded|listed)\\s*[:\\-]?\\s*"
                    + "(przed chwilą|teraz|just now|wczoraj|yesterday|"
                    + "(\\d+)\\s*(sekund(?:a|y|ę|)?|seconds?|secs?|"
                    + "minut(?:a|y|ę|)?|min\\.?|minutes?|mins?|"
                    + "godzin(?:a|y|ę|)?|godz\\.?|hours?|hrs?|"
                    + "dzień|dnia|dni|days?|"
                    + "tydzień|tygodnia|tygodnie|tygodni|weeks?)\\s*(?:temu|ago)?)"
    );

    private static final Pattern POLISH_DATE_PATTERN = Pattern.compile(
            "(?iu)(?:dodane|dodano|wystawione)\\s*[:\\-]?\\s*"
                    + "(\\d{1,2})\\s+"
                    + "(stycznia|lutego|marca|kwietnia|maja|czerwca|lipca|sierpnia|"
                    + "września|wrzesnia|października|pazdziernika|listopada|grudnia)"
                    + "\\s+(\\d{4})(?:\\s+(?:o\\s+)?(\\d{1,2}):(\\d{2}))?"
    );

    Map<String, String> resolve(
            BotContext context,
            List<String> listingIds
    ) {
        if (listingIds == null || listingIds.isEmpty()) {
            return Map.of();
        }

        Page page = context.getPage();
        Map<String, String> listingUrls = captureListingUrls(
                page,
                listingIds
        );
        Map<String, String> result = new LinkedHashMap<>();

        for (int index = 0; index < listingIds.size(); index++) {
            String listingId = listingIds.get(index);

            if (isBlank(listingId)) {
                continue;
            }

            String listingUrl = listingUrls.getOrDefault(
                    listingId,
                    VINTED_BASE_URL + "/items/" + listingId
            );

            try {
                page.navigate(listingUrl);
                page.waitForLoadState();

                LocalDateTime referenceTime = LocalDateTime.now(MARKET_ZONE);
                String bodyText = page.locator("body").innerText();

                Optional<LocalDateTime> publishedAt = parsePublishedAt(
                        bodyText,
                        referenceTime
                );

                if (publishedAt.isEmpty()) {
                    log.warn(
                            "[MARKET STATS] Could not read Vinted publication age. listingId={}, url={}",
                            listingId,
                            listingUrl
                    );
                    continue;
                }

                LocalDateTime resolved = publishedAt.get();
                result.put(listingId, resolved.toString());

                log.info(
                        "[MARKET STATS] Publication time resolved. listingId={}, publishedAt={}, detail={}/{}.",
                        listingId,
                        resolved,
                        index + 1,
                        listingIds.size()
                );
            } catch (RuntimeException exception) {
                log.warn(
                        "[MARKET STATS] Could not inspect listing detail for publication time. listingId={}, url={}, reason={}",
                        listingId,
                        listingUrl,
                        safeMessage(exception)
                );
            }
        }

        return Map.copyOf(result);
    }

    private Map<String, String> captureListingUrls(
            Page page,
            List<String> listingIds
    ) {
        Map<String, String> result = new LinkedHashMap<>();

        for (String listingId : listingIds) {
            if (isBlank(listingId)) {
                continue;
            }

            try {
                Locator links = page.locator(
                        "a[href*='/items/" + listingId + "']"
                );

                if (links.count() == 0) {
                    continue;
                }

                String href = links.first().getAttribute("href");
                String absolute = absoluteVintedUrl(page.url(), href);

                if (!isBlank(absolute)) {
                    result.put(listingId, absolute);
                }
            } catch (RuntimeException exception) {
                log.debug(
                        "[MARKET STATS] Could not capture catalog URL for listing {}. Direct item URL fallback will be used.",
                        listingId,
                        exception
                );
            }
        }

        return result;
    }

    private String absoluteVintedUrl(
            String currentUrl,
            String href
    ) {
        if (isBlank(href)) {
            return null;
        }

        try {
            URI candidate = URI.create(href.trim());

            if (candidate.isAbsolute()) {
                return candidate.toString();
            }

            URI base = isBlank(currentUrl)
                    ? URI.create(VINTED_BASE_URL)
                    : URI.create(currentUrl);

            return base.resolve(candidate).toString();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    static Optional<LocalDateTime> parsePublishedAt(
            String bodyText,
            LocalDateTime referenceTime
    ) {
        if (bodyText == null
                || bodyText.isBlank()
                || referenceTime == null) {
            return Optional.empty();
        }

        Matcher relative = RELATIVE_PATTERN.matcher(bodyText);

        if (relative.find()) {
            String phrase = relative.group(1)
                    .trim()
                    .toLowerCase(Locale.ROOT);

            if (phrase.equals("przed chwilą")
                    || phrase.equals("teraz")
                    || phrase.equals("just now")) {
                return Optional.of(referenceTime);
            }

            if (phrase.equals("wczoraj") || phrase.equals("yesterday")) {
                return Optional.of(referenceTime.minusDays(1));
            }

            int amount = Integer.parseInt(relative.group(2));
            String unit = relative.group(3)
                    .toLowerCase(Locale.ROOT);

            if (unit.startsWith("sekund")
                    || unit.startsWith("second")
                    || unit.startsWith("sec")) {
                return Optional.of(referenceTime.minusSeconds(amount));
            }

            if (unit.startsWith("minut")
                    || unit.equals("min")
                    || unit.equals("min.")
                    || unit.startsWith("minute")
                    || unit.startsWith("mins")) {
                return Optional.of(referenceTime.minusMinutes(amount));
            }

            if (unit.startsWith("godzin")
                    || unit.equals("godz")
                    || unit.equals("godz.")
                    || unit.startsWith("hour")
                    || unit.startsWith("hrs")) {
                return Optional.of(referenceTime.minusHours(amount));
            }

            if (unit.equals("dzień")
                    || unit.equals("dnia")
                    || unit.equals("dni")
                    || unit.startsWith("day")) {
                return Optional.of(referenceTime.minusDays(amount));
            }

            if (unit.startsWith("tydzie")
                    || unit.startsWith("tygod")
                    || unit.startsWith("week")) {
                return Optional.of(referenceTime.minusWeeks(amount));
            }
        }

        Matcher polishDate = POLISH_DATE_PATTERN.matcher(bodyText);

        if (polishDate.find()) {
            int day = Integer.parseInt(polishDate.group(1));
            int month = polishMonth(polishDate.group(2));
            int year = Integer.parseInt(polishDate.group(3));

            String hourGroup = polishDate.group(4);
            String minuteGroup = polishDate.group(5);
            LocalTime time = hourGroup == null || minuteGroup == null
                    ? LocalTime.MIDNIGHT
                    : LocalTime.of(
                            Integer.parseInt(hourGroup),
                            Integer.parseInt(minuteGroup)
                    );

            return Optional.of(
                    LocalDate.of(year, month, day).atTime(time)
            );
        }

        return Optional.empty();
    }

    private static int polishMonth(String rawMonth) {
        String month = rawMonth
                .toLowerCase(Locale.ROOT)
                .replace('ś', 's')
                .replace('ź', 'z')
                .replace('ż', 'z')
                .replace('ą', 'a')
                .replace('ę', 'e')
                .replace('ć', 'c')
                .replace('ń', 'n')
                .replace('ó', 'o')
                .replace('ł', 'l');

        return switch (month) {
            case "stycznia" -> 1;
            case "lutego" -> 2;
            case "marca" -> 3;
            case "kwietnia" -> 4;
            case "maja" -> 5;
            case "czerwca" -> 6;
            case "lipca" -> 7;
            case "sierpnia" -> 8;
            case "wrzesnia" -> 9;
            case "pazdziernika" -> 10;
            case "listopada" -> 11;
            case "grudnia" -> 12;
            default -> throw new IllegalArgumentException(
                    "Unsupported Polish month: " + rawMonth
            );
        };
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String safeMessage(Throwable throwable) {
        if (throwable == null
                || throwable.getMessage() == null
                || throwable.getMessage().isBlank()) {
            return throwable == null
                    ? "unknown error"
                    : throwable.getClass().getSimpleName();
        }

        return throwable.getMessage()
                .lines()
                .findFirst()
                .orElse(throwable.getMessage())
                .trim();
    }
}
