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

    private static final Map<String, Set<String>> OPTIONAL_TARGET_TOKENS_BY_BRAND =
            Map.of(
                    "samsung",
                    Set.of("galaxy")
            );

    private static final Set<String> VARIANT_TOKENS = Set.of(
            "plus",
            "ultra",
            "pro",
            "max",
            "fe",
            "lite",
            "mini",
            "edge",
            "air"
    );

    private static final Set<String> TECHNICAL_MODEL_CODE_PREFIX_TOKENS =
            Set.of("sm");

    private static final Set<String> SPLIT_MODEL_FAMILIES = Set.of(
            "flip",
            "fold",
            "pixel",
            "iphone",
            "note",
            "mate",
            "oneplus",
            "xiaomi"
    );

    private final VintedModelTargetGuard vintedModelTargetGuard =
            new VintedModelTargetGuard();

    public ListingTargetAssessment assessCatalogListing(
            ListingResponseDto listing,
            BotConfigurationDto configuration
    ) {
        if (listing == null) {
            return ListingTargetAssessment.MISMATCH;
        }

        validateConfiguration(configuration);

        String title = normalizeVisibleText(listing.title());
        ListingTargetAssessment assessment = assessVisibleText(
                title,
                configuration
        );

        if (assessment == ListingTargetAssessment.MATCH) {
            log.debug(
                    "[TARGET MATCHER] Marketplace listing {} positively matches {} from catalog title='{}'.",
                    listing.listingId(),
                    resolveTargetMode(configuration),
                    title
            );
        } else if (assessment == ListingTargetAssessment.MISMATCH) {
            log.warn(
                    "[TARGET MATCHER] Marketplace listing {} is a conclusive {} catalog mismatch. Catalog title='{}'.",
                    listing.listingId(),
                    resolveTargetMode(configuration),
                    title
            );
        } else {
            log.info(
                    "[TARGET MATCHER] Marketplace listing {} has incomplete/ambiguous {} catalog identity='{}'. URL or live item identity is required before negotiation.",
                    listing.listingId(),
                    resolveTargetMode(configuration),
                    title
            );
        }

        return assessment;
    }

    public ListingTargetAssessment assessListingUrl(
            ListingResponseDto listing,
            BotConfigurationDto configuration
    ) {
        if (listing == null) {
            return ListingTargetAssessment.MISMATCH;
        }

        validateConfiguration(configuration);

        if (listing.url() == null || listing.url().isBlank()) {
            return ListingTargetAssessment.NEEDS_DETAIL_INSPECTION;
        }

        String slugText = extractSlugText(listing.url());
        if (slugText.isBlank()) {
            return ListingTargetAssessment.NEEDS_DETAIL_INSPECTION;
        }

        ListingTargetAssessment assessment = assessVisibleText(
                slugText,
                configuration
        );

        if (assessment == ListingTargetAssessment.MATCH) {
            log.info(
                    "[TARGET URL] Marketplace listing {} positively matches {} using URL slug='{}'.",
                    listing.listingId(),
                    resolveTargetMode(configuration),
                    slugText
            );
        } else if (assessment == ListingTargetAssessment.MISMATCH) {
            log.warn(
                    "[TARGET URL] Marketplace listing {} is a conclusive {} mismatch using URL slug='{}'.",
                    listing.listingId(),
                    resolveTargetMode(configuration),
                    slugText
            );
        }

        return assessment;
    }

    /**
     * Assesses arbitrary visible identity text.
     *
     * VINTED_MODEL no longer means "blindly trust every persisted DISCOVERED
     * row". A bot-scoped backlog can contain rows discovered under an older
     * broken filter or before a later configuration change. Therefore:
     * - conclusive different model -> MISMATCH,
     * - positive configured model proof -> MATCH,
     * - generic/ambiguous text -> NEEDS_DETAIL_INSPECTION.
     */
    public ListingTargetAssessment assessVisibleText(
            String visibleText,
            BotConfigurationDto configuration
    ) {
        validateConfiguration(configuration);

        String normalizedText = normalizeVisibleText(visibleText);
        if (normalizedText.isBlank()) {
            return ListingTargetAssessment.NEEDS_DETAIL_INSPECTION;
        }

        if (usesVintedModelFilter(configuration)) {
            var mismatch = vintedModelTargetGuard.findConclusiveMismatch(
                    configuration.getModel(),
                    normalizedText
            );

            if (mismatch.isPresent()) {
                log.warn(
                        "[VINTED MODEL TARGET] Conclusive mismatch for configured '{}': {}",
                        configuration.getModel(),
                        mismatch.get()
                );
                return ListingTargetAssessment.MISMATCH;
            }

            if (vintedModelTargetGuard.provesConfiguredModel(
                    configuration.getModel(),
                    normalizedText
            )) {
                return ListingTargetAssessment.MATCH;
            }

            return ListingTargetAssessment.NEEDS_DETAIL_INSPECTION;
        }

        if (matchesTextStrict(normalizedText, configuration)) {
            return ListingTargetAssessment.MATCH;
        }

        if (hasClearConflict(normalizedText, configuration)) {
            return ListingTargetAssessment.MISMATCH;
        }

        return ListingTargetAssessment.NEEDS_DETAIL_INSPECTION;
    }

    public boolean matchesFullTitle(
            String fullTitle,
            BotConfigurationDto configuration
    ) {
        ListingTargetAssessment assessment = assessVisibleText(
                fullTitle,
                configuration
        );

        if (assessment == ListingTargetAssessment.MATCH) {
            log.debug(
                    "[TARGET MATCHER] Full item title positively matches {} target. Full title='{}'.",
                    resolveTargetMode(configuration),
                    normalizeVisibleText(fullTitle)
            );
            return true;
        }

        log.info(
                "[TARGET MATCHER] Full item title does not positively prove {} target. Assessment={}, full title='{}'.",
                resolveTargetMode(configuration),
                assessment,
                normalizeVisibleText(fullTitle)
        );
        return false;
    }

    public boolean matches(
            ListingResponseDto listing,
            BotConfigurationDto configuration
    ) {
        return assessCatalogListing(listing, configuration)
                == ListingTargetAssessment.MATCH;
    }

    boolean usesVintedModelFilter(BotConfigurationDto configuration) {
        return configuration != null
                && VINTED_MODEL.equals(resolveTargetMode(configuration));
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

            if (index + 1 < tokens.size()
                    && SPLIT_MODEL_FAMILIES.contains(token)
                    && tokens.get(index + 1).matches("^\\d+[a-z]*$")) {
                result.add(token + tokens.get(index + 1));
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
