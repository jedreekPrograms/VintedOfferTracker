package pl.flipbot.playwright.filters;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure query-parameter operations extracted from FilterService.
 *
 * Intentionally preserves the original first-match and URL-replacement behavior.
 * This is not the strict evidence check for a verified Vinted model or category;
 * that validation remains with the existing dedicated filter verifiers.
 */
final class VintedCatalogUrlParameters {
    private VintedCatalogUrlParameters() {
    }

    static String get(String currentUrl, String parameterName) {
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
            if (!parameterName.equals(decodedName)) {
                continue;
            }
            return URLDecoder.decode(rawValue, StandardCharsets.UTF_8);
        }
        return null;
    }

    static String withOrReplaced(String url, String parameterName, String parameterValue) {
        int fragmentIndex = url.indexOf('#');
        String fragment = fragmentIndex >= 0 ? url.substring(fragmentIndex) : "";
        String withoutFragment = fragmentIndex >= 0
                ? url.substring(0, fragmentIndex)
                : url;

        Pattern pattern = Pattern.compile(
                "([?&])" + Pattern.quote(parameterName) + "=[^&#]*"
        );
        Matcher matcher = pattern.matcher(withoutFragment);
        if (matcher.find()) {
            return matcher.replaceFirst("$1" + parameterName + "=" + parameterValue) + fragment;
        }

        String separator = withoutFragment.contains("?") ? "&" : "?";
        return withoutFragment + separator + parameterName + "=" + parameterValue + fragment;
    }
}
