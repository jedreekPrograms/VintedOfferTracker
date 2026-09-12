package pl.flipbot.playwright.marketstats;

import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class VintedPublishedAtParser {

    private static final ZoneId MARKET_ZONE = ZoneId.of("Europe/Warsaw");
    private static final Pattern NUMBER_AND_UNIT = Pattern.compile(
            "^(\\d+)\\s*(.+)$"
    );

    private VintedPublishedAtParser() {
    }

    static Optional<LocalDateTime> parse(
            String payload,
            LocalDateTime observedAt
    ) {
        if (payload == null
                || payload.isBlank()
                || observedAt == null) {
            return Optional.empty();
        }

        String trimmed = payload.trim();

        if (trimmed.startsWith("ISO|")) {
            return parseAbsolute(trimmed.substring(4).trim());
        }

        if (trimmed.startsWith("REL|")) {
            return parseRelative(
                    trimmed.substring(4).trim(),
                    observedAt
            );
        }

        return Optional.empty();
    }

    private static Optional<LocalDateTime> parseAbsolute(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        String trimmed = value.trim();

        if (trimmed.matches("\\d{10,13}")) {
            try {
                long epoch = Long.parseLong(trimmed);
                Instant instant = trimmed.length() <= 10
                        ? Instant.ofEpochSecond(epoch)
                        : Instant.ofEpochMilli(epoch);
                return Optional.of(
                        LocalDateTime.ofInstant(instant, MARKET_ZONE)
                );
            } catch (NumberFormatException | DateTimeException ignored) {
                return Optional.empty();
            }
        }

        try {
            return Optional.of(
                    LocalDateTime.ofInstant(
                            Instant.parse(trimmed),
                            MARKET_ZONE
                    )
            );
        } catch (DateTimeException ignored) {
            // Try the remaining ISO variants below.
        }

        try {
            return Optional.of(
                    OffsetDateTime.parse(trimmed)
                            .atZoneSameInstant(MARKET_ZONE)
                            .toLocalDateTime()
            );
        } catch (DateTimeException ignored) {
            // Try the remaining ISO variants below.
        }

        try {
            return Optional.of(
                    ZonedDateTime.parse(trimmed)
                            .withZoneSameInstant(MARKET_ZONE)
                            .toLocalDateTime()
            );
        } catch (DateTimeException ignored) {
            // Try a timezone-free value below.
        }

        try {
            return Optional.of(LocalDateTime.parse(trimmed));
        } catch (DateTimeException ignored) {
            // Try a date-only value below.
        }

        try {
            return Optional.of(LocalDate.parse(trimmed).atStartOfDay());
        } catch (DateTimeException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<LocalDateTime> parseRelative(
            String value,
            LocalDateTime observedAt
    ) {
        String normalized = normalize(value);

        if (normalized.isBlank()) {
            return Optional.empty();
        }

        if (normalized.equals("teraz")
                || normalized.startsWith("przed chwila")
                || normalized.equals("dzisiaj")) {
            return Optional.of(observedAt);
        }

        if (normalized.equals("wczoraj")) {
            return Optional.of(observedAt.minusDays(1L));
        }

        if (normalized.equals("przedwczoraj")) {
            return Optional.of(observedAt.minusDays(2L));
        }

        /*
         * Confirmed from Vinted's live item DOM. The exact
         * [data-testid="item-attributes-upload_date"] field can render
         * singular relative ages without a numeric prefix or datetime.
         */
        if (normalized.equals("godziny")) {
            return Optional.of(observedAt.minusHours(1L));
        }

        if (normalized.equals("tygodnia")) {
            return Optional.of(observedAt.minusWeeks(1L));
        }

        if (normalized.equals("roku")) {
            return Optional.of(observedAt.minusYears(1L));
        }

        Matcher matcher = NUMBER_AND_UNIT.matcher(normalized);

        if (!matcher.matches()) {
            return Optional.empty();
        }

        long amount;

        try {
            amount = Long.parseLong(matcher.group(1));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }

        String unit = matcher.group(2).trim();

        if (unit.startsWith("sek") || unit.equals("s")) {
            return Optional.of(observedAt.minusSeconds(amount));
        }

        if (unit.startsWith("min")) {
            return Optional.of(observedAt.minusMinutes(amount));
        }

        if (unit.startsWith("godz") || unit.equals("h")) {
            return Optional.of(observedAt.minusHours(amount));
        }

        if (unit.startsWith("dzien")
                || unit.startsWith("dnia")
                || unit.startsWith("dni")
                || unit.equals("d")) {
            return Optional.of(observedAt.minusDays(amount));
        }

        if (unit.startsWith("tyg")) {
            return Optional.of(observedAt.minusWeeks(amount));
        }

        if (unit.startsWith("mies")) {
            return Optional.of(observedAt.minusMonths(amount));
        }

        if (unit.startsWith("rok")
                || unit.startsWith("lata")
                || unit.startsWith("lat")) {
            return Optional.of(observedAt.minusYears(amount));
        }

        return Optional.empty();
    }

    private static String normalize(String value) {
        String normalized = Normalizer.normalize(
                value == null ? "" : value,
                Normalizer.Form.NFD
        );

        return normalized
                .replaceAll("\\p{M}+", "")
                .replace('\u00A0', ' ')
                .toLowerCase(Locale.ROOT)
                .replace(".", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
