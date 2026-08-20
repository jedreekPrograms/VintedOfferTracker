package pl.flipbot.playwright.target;

import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.model.BotConfigurationDto;

import java.net.URI;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class ListingTargetMatcher {

    private static final String VINTED_MODEL = "VINTED_MODEL";
    private static final String SEARCH_QUERY = "SEARCH_QUERY";

    private static final Pattern COMPACT_MODEL_TOKEN_PATTERN =
            Pattern.compile("^([a-z]+)(\\d+[a-z]*)$");

    private static final Pattern LEADING_LISTING_ID_PATTERN =
            Pattern.compile("^\\d+-?");

    /*
     * Optional brand-specific target tokens.
     * For Samsung, "galaxy" may be omitted by sellers without changing the
     * actual model identity.
     */
    private static final Map<String, Set<String>> OPTIONAL_TARGET_TOKENS_BY_BRAND =
            Map.of(
                    "samsung",
                    Set.of("galaxy")
            );

    private static final Set<String> VARIANT_TOKENS =
            Set.of(
                    "plus",
                    "ultra",
                    "pro",
                    "max",
                    "fe",
                    "lite",
                    "mini",
                    "edge"
            );

    /*
     * Manufacturer product-code markers are not marketing model names.
     *
     * Example:
     *   Samsung Galaxy S25 SM-S931 12/128GB
     *
     * Tokenization gives: s25, sm, s931, ...
     * s931 must NOT conflict with the target model key s25. The previous
     * implementation treated both as competing "s..." models and rejected a
     * perfectly valid S25. We ignore compact model-like tokens immediately
     * preceded by a known technical-code marker while still detecting real
     * conflicts such as S24 vs S25.
     */
    private static final Set<String> TECHNICAL_MODEL_CODE_PREFIX_TOKENS =
            Set.of("sm");

    public ListingTargetAssessment assessCatalogListing(
            ListingResponseDto listing,
            BotConfigurationDto configuration
    ) {
        if (listing == null) {
            return ListingTargetAssessment.MISMATCH;
        }

        validateConfiguration(configuration);

        String title = normalizeVisibleText(listing.title());
        if (title.isBlank()) {
            log.info(
                    "[TARGET MATCHER] Marketplace listing {} has no usable catalog title. URL/detail inspection is required.",
                    listing.listingId()
            );
            return ListingTargetAssessment.NEEDS_DETAIL_INSPECTION;
        }

        if (matchesTextStrict(title, configuration)) {
            log.debug(
                    "[TARGET MATCHER] Marketplace listing {} already matches using catalog title='{}'.",
                    listing.listingId(),
                    title
            );
            return ListingTargetAssessment.MATCH;
        }

        if (hasClearConflict(title, configuration)) {
            log.info(
                    "[TARGET MATCHER] Marketplace listing {} is a clear catalog mismatch. Catalog title='{}'.",
                    listing.listingId(),
                    title
            );
            return ListingTargetAssessment.MISMATCH;
        }

        log.info(
                "[TARGET MATCHER] Marketplace listing {} has an incomplete or ambiguous catalog title='{}'. URL slug will be checked before opening the item page.",
                listing.listingId(),
                title
        );
        return ListingTargetAssessment.NEEDS_DETAIL_INSPECTION;
    }

    public ListingTargetAssessment assessListingUrl(
            ListingResponseDto listing,
            BotConfigurationDto configuration
    ) {
        if (listing == null
                || listing.url() == null
                || listing.url().isBlank()) {
            return ListingTargetAssessment.NEEDS_DETAIL_INSPECTION;
        }

        validateConfiguration(configuration);

        String slugText = extractSlugText(listing.url());
        if (slugText.isBlank()) {
            log.debug(
                    "[TARGET URL] Marketplace listing {} has no usable descriptive URL slug.",
                    listing.listingId()
            );
            return ListingTargetAssessment.NEEDS_DETAIL_INSPECTION;
        }

        if (matchesTextStrict(slugText, configuration)) {
            log.info(
                    "[TARGET URL] Marketplace listing {} matches using URL slug='{}'. No detail-page request is needed.",
                    listing.listingId(),
                    slugText
            );
            return ListingTargetAssessment.MATCH;
        }

        if (hasClearConflict(slugText, configuration)) {
            log.info(
                    "[TARGET URL] Marketplace listing {} is a clear mismatch using URL slug='{}'. No detail-page request is needed.",
                    listing.listingId(),
                    slugText
            );
            return ListingTargetAssessment.MISMATCH;
        }

        log.debug(
                "[TARGET URL] Marketplace listing {} URL slug='{}' is still ambiguous. Detail-page inspection may be required.",
                listing.listingId(),
                slugText
        );
        return ListingTargetAssessment.NEEDS_DETAIL_INSPECTION;
    }

    public boolean matchesFullTitle(
            String fullTitle,
            BotConfigurationDto configuration
    ) {
        validateConfiguration(configuration);

        String normalizedTitle = normalizeVisibleText(fullTitle);
        if (normalizedTitle.isBlank()) {
            return false;
        }

        boolean matches = matchesTextStrict(
                normalizedTitle,
                configuration
        );

        if (matches) {
            log.debug(
                    "[TARGET MATCHER] Full item title matches target. Full title='{}'.",
                    normalizedTitle
            );
        } else {
            log.info(
                    "[TARGET MATCHER] Full item title does not match target. Full title='{}'.",
                    normalizedTitle
            );
        }

        return matches;
    }

    public boolean matches(
            ListingResponseDto listing,
            BotConfigurationDto configuration
    ) {
        return assessCatalogListing(listing, configuration)
                == ListingTargetAssessment.MATCH;
    }

    private boolean matchesTextStrict(
            String candidateText,
            BotConfigurationDto configuration
    ) {
        String rawTarget = resolveRawTarget(configuration);
        List<String> targetTokens = buildMeaningfulTargetTokens(
                rawTarget,
                configuration.getBrand()
        );

        if (targetTokens.isEmpty()) {
            throw new IllegalStateException(
                    "Configured target produced no meaningful tokens"
            );
        }

        List<String> candidateTokens = tokenize(candidateText);

        if (hasUnexpectedVariant(targetTokens, candidateTokens)) {
            return false;
        }

        if (hasConflictingModelKey(targetTokens, candidateTokens)) {
            return false;
        }

        return allTargetTokensMatch(targetTokens, candidateTokens);
    }

    private boolean hasClearConflict(
            String candidateText,
            BotConfigurationDto configuration
    ) {
        String rawTarget = resolveRawTarget(configuration);
        List<String> targetTokens = buildMeaningfulTargetTokens(
                rawTarget,
                configuration.getBrand()
        );
        List<String> candidateTokens = tokenize(candidateText);

        return hasUnexpectedVariant(targetTokens, candidateTokens)
                || hasConflictingModelKey(targetTokens, candidateTokens);
    }

    private boolean hasConflictingModelKey(
            List<String> targetTokens,
            List<String> candidateTokens
    ) {
        List<String> targetModelKeys = extractModelKeys(targetTokens);
        if (targetModelKeys.isEmpty()) {
            return false;
        }

        List<String> candidateModelKeys = extractModelKeys(candidateTokens);

        for (String targetKey : targetModelKeys) {
            String targetPrefix = extractAlphabeticPrefix(targetKey);
            if (targetPrefix.isBlank()) {
                continue;
            }

            for (String candidateKey : candidateModelKeys) {
                String candidatePrefix = extractAlphabeticPrefix(candidateKey);

                if (targetPrefix.equals(candidatePrefix)
                        && !targetKey.equals(candidateKey)) {
                    return true;
                }
            }
        }

        return false;
    }

    /*
     * Examples:
     * s11       -> s11
     * iphone 17 -> iphone17
     * pixel 10  -> pixel10
     * fold 8    -> fold8
     */
    private List<String> extractModelKeys(List<String> tokens) {
        List<String> result = new ArrayList<>();

        for (int index = 0; index < tokens.size(); index++) {
            String token = tokens.get(index);

            Matcher compactMatcher = COMPACT_MODEL_TOKEN_PATTERN.matcher(token);
            if (compactMatcher.matches()) {
                if (isTechnicalProductCode(tokens, index)) {
                    continue;
                }

                result.add(token);
                continue;
            }

            if (token.matches("^\\d+[a-z]*$") && index > 0) {
                String previousToken = tokens.get(index - 1);

                if (previousToken.matches("^[a-z]+$")
                        && !VARIANT_TOKENS.contains(previousToken)
                        && !TECHNICAL_MODEL_CODE_PREFIX_TOKENS.contains(previousToken)) {
                    result.add(previousToken + token);
                }
            }
        }

        return result;
    }

    private boolean isTechnicalProductCode(
            List<String> tokens,
            int tokenIndex
    ) {
        if (tokenIndex <= 0) {
            return false;
        }

        return TECHNICAL_MODEL_CODE_PREFIX_TOKENS.contains(
                tokens.get(tokenIndex - 1)
        );
    }

    private String extractAlphabeticPrefix(String modelKey) {
        Matcher matcher = Pattern.compile("^([a-z]+)").matcher(modelKey);
        if (!matcher.find()) {
            return "";
        }
        return matcher.group(1);
    }

    private void validateConfiguration(BotConfigurationDto configuration) {
        if (configuration == null) {
            throw new IllegalArgumentException(
                    "Bot configuration cannot be null"
            );
        }

        String rawTarget = resolveRawTarget(configuration);
        if (rawTarget == null || rawTarget.isBlank()) {
            throw new IllegalStateException(
                    "Bot configuration has no usable target for mode "
                            + resolveTargetMode(configuration)
            );
        }
    }

    private String resolveRawTarget(BotConfigurationDto configuration) {
        String targetMode = resolveTargetMode(configuration);
        if (SEARCH_QUERY.equals(targetMode)) {
            return configuration.getSearchQuery();
        }
        return configuration.getModel();
    }

    private String resolveTargetMode(BotConfigurationDto configuration) {
        String targetMode = configuration.getTargetMode();
        if (targetMode == null || targetMode.isBlank()) {
            return VINTED_MODEL;
        }

        String normalizedMode = targetMode
                .trim()
                .toUpperCase(Locale.ROOT);

        if (VINTED_MODEL.equals(normalizedMode)
                || SEARCH_QUERY.equals(normalizedMode)) {
            return normalizedMode;
        }

        throw new IllegalStateException(
                "Unsupported target mode: " + targetMode
        );
    }

    private List<String> buildMeaningfulTargetTokens(
            String rawTarget,
            String configuredBrand
    ) {
        List<String> targetTokens = tokenize(rawTarget);
        Set<String> brandTokens = new HashSet<>(tokenize(configuredBrand));
        Set<String> optionalTokensForBrand = getOptionalTokensForBrand(
                configuredBrand
        );

        List<String> meaningfulTokens = new ArrayList<>();
        for (String token : targetTokens) {
            if (brandTokens.contains(token)) {
                continue;
            }
            if (optionalTokensForBrand.contains(token)) {
                continue;
            }
            meaningfulTokens.add(token);
        }

        return meaningfulTokens;
    }

    private Set<String> getOptionalTokensForBrand(String configuredBrand) {
        List<String> normalizedBrandTokens = tokenize(configuredBrand);
        if (normalizedBrandTokens.isEmpty()) {
            return Set.of();
        }

        String normalizedBrand = String.join(" ", normalizedBrandTokens);
        return OPTIONAL_TARGET_TOKENS_BY_BRAND.getOrDefault(
                normalizedBrand,
                Set.of()
        );
    }

    private boolean allTargetTokensMatch(
            List<String> targetTokens,
            List<String> candidateTokens
    ) {
        boolean[] matched = new boolean[targetTokens.size()];

        for (int targetIndex = 0;
                targetIndex < targetTokens.size();
                targetIndex++) {
            String targetToken = targetTokens.get(targetIndex);
            if (candidateTokens.contains(targetToken)) {
                matched[targetIndex] = true;
            }
        }

        /*
         * Joined variants such as S11Ultra, TabS11Ultra, Fold8Ultra.
         */
        for (String candidateToken : candidateTokens) {
            for (int start = 0; start < targetTokens.size(); start++) {
                StringBuilder joined = new StringBuilder();

                for (int end = start; end < targetTokens.size(); end++) {
                    joined.append(targetTokens.get(end));

                    if (candidateToken.equals(joined.toString())) {
                        for (int index = start; index <= end; index++) {
                            matched[index] = true;
                        }
                    }
                }
            }
        }

        for (boolean tokenMatched : matched) {
            if (!tokenMatched) {
                return false;
            }
        }

        return true;
    }

    private boolean hasUnexpectedVariant(
            List<String> targetTokens,
            List<String> candidateTokens
    ) {
        Set<String> targetVariants = new HashSet<>();

        for (String token : targetTokens) {
            if (VARIANT_TOKENS.contains(token)) {
                targetVariants.add(token);
            }
        }

        for (String candidateToken : candidateTokens) {
            if (VARIANT_TOKENS.contains(candidateToken)
                    && !targetVariants.contains(candidateToken)) {
                return true;
            }
        }

        return false;
    }

    private String extractSlugText(String url) {
        try {
            URI uri = URI.create(url);
            String path = uri.getPath();

            if (path == null || path.isBlank()) {
                return "";
            }

            int lastSlashIndex = path.lastIndexOf('/');
            String lastSegment = lastSlashIndex >= 0
                    ? path.substring(lastSlashIndex + 1)
                    : path;

            String withoutListingId = LEADING_LISTING_ID_PATTERN
                    .matcher(lastSegment)
                    .replaceFirst("");

            return withoutListingId.replace('-', ' ');

        } catch (IllegalArgumentException exception) {
            log.debug(
                    "[TARGET URL] Could not parse listing URL '{}'.",
                    url,
                    exception
            );
            return "";
        }
    }

    private String normalizeVisibleText(String value) {
        if (value == null) {
            return "";
        }

        return value
                .trim()
                .replaceAll("\\s+", " ");
    }

    private List<String> tokenize(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }

        String preparedValue = value
                .replace("+", " plus ")
                .replace("＋", " plus ");

        String normalized = Normalizer.normalize(
                        preparedValue,
                        Normalizer.Form.NFD
                )
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");

        if (normalized.isBlank()) {
            return List.of();
        }

        String[] rawTokens = normalized.split(" ");
        List<String> result = new ArrayList<>();

        for (int index = 0; index < rawTokens.length; index++) {
            String token = rawTokens[index];

            /*
             * "s 11" -> "s11", "a 55" -> "a55".
             */
            if (token.matches("^[a-z]$")
                    && index + 1 < rawTokens.length
                    && rawTokens[index + 1].matches("^\\d+[a-z]*$")) {
                result.add(token + rawTokens[index + 1]);
                index++;
                continue;
            }

            result.add(token);
        }

        return result;
    }
}
