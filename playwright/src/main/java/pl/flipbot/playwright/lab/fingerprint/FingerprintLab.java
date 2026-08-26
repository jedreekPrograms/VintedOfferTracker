package pl.flipbot.playwright.lab.fingerprint;

import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Local-only browser fingerprint experiment.
 *
 * This class is intentionally not wired into BotContext, BrowserManager or any
 * marketplace workflow. It mutates only the currently loaded laboratory
 * document via page.evaluate(), after the policy has verified the page URL.
 * Navigating away creates a fresh document and removes these overrides.
 */
public final class FingerprintLab {

    private FingerprintLab() {}

    public static void apply(
            Page page,
            FingerprintLabProfile profile
    ) {
        Objects.requireNonNull(page, "page cannot be null");
        Objects.requireNonNull(profile, "profile cannot be null");

        FingerprintLabPolicy.requireAllowed(page.url());

        Map<String, Object> values = Map.of(
                "platform", profile.platform(),
                "hardwareConcurrency", profile.hardwareConcurrency(),
                "deviceMemoryGb", profile.deviceMemoryGb(),
                "languages", profile.languages()
        );

        page.evaluate(
                """
                profile => {
                  const defineGetter = (name, value) => {
                    Object.defineProperty(
                      Navigator.prototype,
                      name,
                      {
                        configurable: true,
                        get: () => value
                      }
                    );
                  };

                  const languages = Object.freeze([...profile.languages]);

                  defineGetter('platform', profile.platform);
                  defineGetter(
                    'hardwareConcurrency',
                    profile.hardwareConcurrency
                  );
                  defineGetter('deviceMemory', profile.deviceMemoryGb);
                  defineGetter('language', languages[0]);
                  defineGetter('languages', languages);

                  Object.defineProperty(
                    window,
                    '__flipbotFingerprintLab',
                    {
                      configurable: true,
                      enumerable: false,
                      writable: false,
                      value: Object.freeze({
                        active: true,
                        scope: 'local-lab-only',
                        changedSurfaces: Object.freeze([
                          'navigator.platform',
                          'navigator.hardwareConcurrency',
                          'navigator.deviceMemory',
                          'navigator.language',
                          'navigator.languages'
                        ])
                      })
                    }
                  );
                }
                """,
                values
        );
    }

    public static FingerprintSnapshot capture(Page page) {
        Objects.requireNonNull(page, "page cannot be null");

        if (!FingerprintLabPolicy.isAllowedUrl(page.url())) {
            throw new IllegalStateException(
                    "Fingerprint snapshot is restricted to laboratory URLs: "
                            + page.url()
            );
        }

        Object raw = page.evaluate(
                """
                () => ({
                  platform: String(navigator.platform || ''),
                  hardwareConcurrency: Number(
                    navigator.hardwareConcurrency || 0
                  ),
                  deviceMemoryGb:
                    typeof navigator.deviceMemory === 'number'
                      ? Number(navigator.deviceMemory)
                      : null,
                  language: String(navigator.language || ''),
                  languages: Array.from(navigator.languages || [])
                    .map(value => String(value)),
                  userAgent: String(navigator.userAgent || ''),
                  labMarkerPresent:
                    Boolean(window.__flipbotFingerprintLab?.active)
                })
                """
        );

        if (!(raw instanceof Map<?, ?> map)) {
            throw new IllegalStateException(
                    "Unexpected fingerprint snapshot payload: " + raw
            );
        }

        return new FingerprintSnapshot(
                stringValue(map.get("platform")),
                intValue(map.get("hardwareConcurrency")),
                nullableIntValue(map.get("deviceMemoryGb")),
                stringValue(map.get("language")),
                stringListValue(map.get("languages")),
                stringValue(map.get("userAgent")),
                Boolean.TRUE.equals(map.get("labMarkerPresent"))
        );
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    private static Integer nullableIntValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    private static List<String> stringListValue(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }

        List<String> result = new ArrayList<>();
        for (Object item : list) {
            result.add(String.valueOf(item));
        }
        return List.copyOf(result);
    }
}
