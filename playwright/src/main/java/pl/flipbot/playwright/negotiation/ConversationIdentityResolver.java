package pl.flipbot.playwright.negotiation;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Resolves Vinted conversation identity while preserving fail-closed behavior.
 *
 * <p>Vinted can migrate an older conversation route (for example a UUID-like
 * inbox id) to a newer numeric inbox id. The observed redirect keeps the old
 * route in the {@code referrer} query parameter. We accept that transition only
 * when the final page is still on Vinted and the referrer proves that the new
 * route came from the exact expected conversation. Any unrelated redirect
 * remains a hard mismatch.</p>
 */
final class ConversationIdentityResolver {

    ConversationIdentityAssessment assess(
            String expectedConversationId,
            String currentUrl
    ) {
        if (expectedConversationId == null
                || expectedConversationId.isBlank()) {
            throw new IllegalArgumentException(
                    "Expected conversation ID cannot be blank"
            );
        }

        if (currentUrl == null || currentUrl.isBlank()) {
            throw new IllegalArgumentException(
                    "Current conversation URL cannot be blank"
            );
        }

        URI current = URI.create(currentUrl);
        requireTrustedVintedUri(current, currentUrl);

        String actualConversationId =
                extractConversationId(current.getPath(), currentUrl);

        if (Objects.equals(
                expectedConversationId,
                actualConversationId
        )) {
            return ConversationIdentityAssessment.exact(
                    actualConversationId,
                    canonicalUrl(current, actualConversationId)
            );
        }

        String referrer = queryParameter(
                current,
                "referrer"
        );

        String referrerConversationId =
                extractConversationIdFromReferrer(referrer);

        if (Objects.equals(
                expectedConversationId,
                referrerConversationId
        )) {
            return ConversationIdentityAssessment.canonicalRedirect(
                    expectedConversationId,
                    actualConversationId,
                    canonicalUrl(current, actualConversationId),
                    referrer
            );
        }

        return ConversationIdentityAssessment.mismatch(
                expectedConversationId,
                actualConversationId,
                currentUrl,
                referrer
        );
    }

    private void requireTrustedVintedUri(
            URI uri,
            String originalUrl
    ) {
        String scheme = uri.getScheme();
        String host = uri.getHost();

        boolean trustedHost = host != null
                && (
                "vinted.pl".equalsIgnoreCase(host)
                        || host.toLowerCase().endsWith(".vinted.pl")
        );

        if (!"https".equalsIgnoreCase(scheme) || !trustedHost) {
            throw new IllegalArgumentException(
                    "Conversation URL is not a trusted Vinted HTTPS URL: "
                            + originalUrl
            );
        }
    }

    private String extractConversationId(
            String path,
            String source
    ) {
        String conversationId =
                tryExtractConversationId(path);

        if (conversationId == null) {
            throw new IllegalArgumentException(
                    "Cannot extract conversation ID from URL/path: "
                            + source
            );
        }

        return conversationId;
    }

    private String extractConversationIdFromReferrer(
            String referrer
    ) {
        if (referrer == null || referrer.isBlank()) {
            return null;
        }

        try {
            URI uri = URI.create(referrer);
            String path = uri.getPath();

            if (path == null || path.isBlank()) {
                path = referrer;
            }

            return tryExtractConversationId(path);
        } catch (IllegalArgumentException exception) {
            return tryExtractConversationId(referrer);
        }
    }

    private String tryExtractConversationId(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }

        String[] parts = path.split("/");

        for (int i = 0; i < parts.length - 1; i++) {
            if ("inbox".equals(parts[i])
                    && parts[i + 1] != null
                    && !parts[i + 1].isBlank()) {
                return parts[i + 1];
            }
        }

        return null;
    }

    private String queryParameter(
            URI uri,
            String requestedName
    ) {
        String rawQuery = uri.getRawQuery();

        if (rawQuery == null || rawQuery.isBlank()) {
            return null;
        }

        for (String pair : rawQuery.split("&")) {
            int separator = pair.indexOf('=');
            String rawName = separator >= 0
                    ? pair.substring(0, separator)
                    : pair;
            String rawValue = separator >= 0
                    ? pair.substring(separator + 1)
                    : "";

            String name = URLDecoder.decode(
                    rawName,
                    StandardCharsets.UTF_8
            );

            if (!requestedName.equals(name)) {
                continue;
            }

            return URLDecoder.decode(
                    rawValue,
                    StandardCharsets.UTF_8
            );
        }

        return null;
    }

    private String canonicalUrl(
            URI current,
            String conversationId
    ) {
        try {
            return new URI(
                    current.getScheme(),
                    current.getAuthority(),
                    "/inbox/" + conversationId,
                    null,
                    null
            ).toString();
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "Could not build canonical conversation URL from "
                            + current,
                    exception
            );
        }
    }

    record ConversationIdentityAssessment(
            MatchType matchType,
            String expectedConversationId,
            String actualConversationId,
            String canonicalConversationUrl,
            String observedUrl,
            String referrer
    ) {
        static ConversationIdentityAssessment exact(
                String conversationId,
                String canonicalConversationUrl
        ) {
            return new ConversationIdentityAssessment(
                    MatchType.EXACT,
                    conversationId,
                    conversationId,
                    canonicalConversationUrl,
                    canonicalConversationUrl,
                    null
            );
        }

        static ConversationIdentityAssessment canonicalRedirect(
                String expectedConversationId,
                String actualConversationId,
                String canonicalConversationUrl,
                String referrer
        ) {
            return new ConversationIdentityAssessment(
                    MatchType.CANONICAL_REDIRECT,
                    expectedConversationId,
                    actualConversationId,
                    canonicalConversationUrl,
                    canonicalConversationUrl,
                    referrer
            );
        }

        static ConversationIdentityAssessment mismatch(
                String expectedConversationId,
                String actualConversationId,
                String observedUrl,
                String referrer
        ) {
            return new ConversationIdentityAssessment(
                    MatchType.MISMATCH,
                    expectedConversationId,
                    actualConversationId,
                    null,
                    observedUrl,
                    referrer
            );
        }

        boolean matchesExpectedConversation() {
            return matchType != MatchType.MISMATCH;
        }

        boolean canonicalRedirect() {
            return matchType == MatchType.CANONICAL_REDIRECT;
        }
    }

    enum MatchType {
        EXACT,
        CANONICAL_REDIRECT,
        MISMATCH
    }
}
