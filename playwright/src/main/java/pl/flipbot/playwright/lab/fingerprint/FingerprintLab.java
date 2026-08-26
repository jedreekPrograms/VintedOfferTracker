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
 * marketplace workflow. It may only run on URLs accepted by
 * FingerprintLabPolicy.
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
        page.evaluate(FingerprintLabScript.build(profile));
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
                () => {
                  const looksNative = value => {
                    if (typeof value !== 'function') {
                      return false;
                    }
                    try {
                      return /\\{\\s*\\[native code\\]\\s*\\}/
                        .test(Function.prototype.toString.call(value));
                    } catch (_) {
                      return false;
                    }
                  };

                  let webglVendor = '';
                  let webglRenderer = '';
                  let webglGetParameterLooksNative = false;

                  try {
                    const canvas = document.createElement('canvas');
                    const gl = canvas.getContext('webgl2')
                      || canvas.getContext('webgl');
                    if (gl) {
                      webglVendor = String(gl.getParameter(37445) || '');
                      webglRenderer = String(gl.getParameter(37446) || '');
                      webglGetParameterLooksNative =
                        looksNative(Object.getPrototypeOf(gl).getParameter);
                    }
                  } catch (_) {
                    // Missing WebGL is itself an observable signal.
                  }

                  const platformDescriptor =
                    Object.getOwnPropertyDescriptor(
                      Navigator.prototype,
                      'platform'
                    );

                  const uaData = navigator.userAgentData;

                  return {
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
                    maxTouchPoints: Number(navigator.maxTouchPoints || 0),
                    screenWidth: Number(screen.width || 0),
                    screenHeight: Number(screen.height || 0),
                    availWidth: Number(screen.availWidth || 0),
                    availHeight: Number(screen.availHeight || 0),
                    colorDepth: Number(screen.colorDepth || 0),
                    devicePixelRatio: Number(window.devicePixelRatio || 0),
                    timezone: String(
                      Intl.DateTimeFormat().resolvedOptions().timeZone || ''
                    ),
                    userAgent: String(navigator.userAgent || ''),
                    webdriver: Boolean(navigator.webdriver),
                    userAgentDataPresent: Boolean(uaData),
                    userAgentDataPlatform: uaData
                      ? String(uaData.platform || '')
                      : '',
                    userAgentDataMobile: uaData
                      ? Boolean(uaData.mobile)
                      : false,
                    webglVendor,
                    webglRenderer,
                    labMarkerPresent:
                      Boolean(window.__flipbotFingerprintLab?.active),
                    navigatorPlatformGetterLooksNative:
                      Boolean(platformDescriptor?.get)
                      && looksNative(platformDescriptor.get),
                    webglGetParameterLooksNative,
                    canvasToDataUrlLooksNative:
                      looksNative(HTMLCanvasElement.prototype.toDataURL)
                  };
                }
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
                intValue(map.get("maxTouchPoints")),
                intValue(map.get("screenWidth")),
                intValue(map.get("screenHeight")),
                intValue(map.get("availWidth")),
                intValue(map.get("availHeight")),
                intValue(map.get("colorDepth")),
                doubleValue(map.get("devicePixelRatio")),
                stringValue(map.get("timezone")),
                stringValue(map.get("userAgent")),
                booleanValue(map.get("webdriver")),
                booleanValue(map.get("userAgentDataPresent")),
                stringValue(map.get("userAgentDataPlatform")),
                booleanValue(map.get("userAgentDataMobile")),
                stringValue(map.get("webglVendor")),
                stringValue(map.get("webglRenderer")),
                booleanValue(map.get("labMarkerPresent")),
                booleanValue(map.get("navigatorPlatformGetterLooksNative")),
                booleanValue(map.get("webglGetParameterLooksNative")),
                booleanValue(map.get("canvasToDataUrlLooksNative"))
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

    private static double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return 0.0;
    }

    private static Integer nullableIntValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    private static boolean booleanValue(Object value) {
        return Boolean.TRUE.equals(value);
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
