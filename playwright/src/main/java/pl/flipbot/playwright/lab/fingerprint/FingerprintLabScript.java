package pl.flipbot.playwright.lab.fingerprint;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Builds the JavaScript used by the dedicated local lab context.
 *
 * This is a teaching simulator, not a stealth layer. Overrides remain easy to
 * detect (native function source is not forged) and a visible lab marker is
 * installed so defensive checks can demonstrate tamper detection.
 */
final class FingerprintLabScript {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private FingerprintLabScript() {}

    static String build(FingerprintLabProfile profile) {
        final String profileJson;

        try {
            profileJson = OBJECT_MAPPER.writeValueAsString(profile);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Could not serialize fingerprint lab profile",
                    exception
            );
        }

        return """
                (() => {
                  const profile = %s;

                  const defineGetter = (prototype, name, value) => {
                    Object.defineProperty(prototype, name, {
                      configurable: true,
                      enumerable: true,
                      get: () => value
                    });
                  };

                  const languages = Object.freeze([...profile.languages]);

                  defineGetter(Navigator.prototype, 'platform', profile.platform);
                  defineGetter(
                    Navigator.prototype,
                    'hardwareConcurrency',
                    profile.hardwareConcurrency
                  );
                  defineGetter(
                    Navigator.prototype,
                    'deviceMemory',
                    profile.deviceMemoryGb
                  );
                  defineGetter(Navigator.prototype, 'language', languages[0]);
                  defineGetter(Navigator.prototype, 'languages', languages);
                  defineGetter(
                    Navigator.prototype,
                    'maxTouchPoints',
                    profile.maxTouchPoints
                  );

                  const screenPrototype = Object.getPrototypeOf(window.screen);
                  defineGetter(screenPrototype, 'width', profile.screenWidth);
                  defineGetter(screenPrototype, 'height', profile.screenHeight);
                  defineGetter(screenPrototype, 'availWidth', profile.availWidth);
                  defineGetter(screenPrototype, 'availHeight', profile.availHeight);
                  defineGetter(screenPrototype, 'colorDepth', profile.colorDepth);
                  defineGetter(screenPrototype, 'pixelDepth', profile.colorDepth);

                  Object.defineProperty(window, 'devicePixelRatio', {
                    configurable: true,
                    enumerable: true,
                    get: () => profile.deviceScaleFactor
                  });

                  const patchWebGl = prototype => {
                    if (!prototype?.getParameter) {
                      return;
                    }

                    const original = prototype.getParameter;
                    prototype.getParameter = function(parameter) {
                      // WEBGL_debug_renderer_info constants.
                      if (parameter === 37445) {
                        return profile.webglVendor;
                      }
                      if (parameter === 37446) {
                        return profile.webglRenderer;
                      }
                      return original.call(this, parameter);
                    };
                  };

                  patchWebGl(globalThis.WebGLRenderingContext?.prototype);
                  patchWebGl(globalThis.WebGL2RenderingContext?.prototype);

                  /*
                   * Demonstration-only canvas perturbation. It deliberately
                   * leaves an observable non-native function and lab marker so
                   * defensive code can detect that the surface was hooked.
                   */
                  const canvasPrototype = globalThis.HTMLCanvasElement?.prototype;
                  if (canvasPrototype?.toDataURL) {
                    const originalToDataURL = canvasPrototype.toDataURL;
                    canvasPrototype.toDataURL = function(...args) {
                      const context = this.getContext?.('2d');
                      if (!context || this.width < 1 || this.height < 1) {
                        return originalToDataURL.apply(this, args);
                      }

                      let originalPixel = null;
                      try {
                        originalPixel = context.getImageData(0, 0, 1, 1);
                        const modified = new ImageData(
                          new Uint8ClampedArray(originalPixel.data),
                          1,
                          1
                        );
                        modified.data[0] =
                          (modified.data[0] + (profile.canvasNoiseSeed %% 7) + 1)
                          %% 256;
                        context.putImageData(modified, 0, 0);
                        return originalToDataURL.apply(this, args);
                      } finally {
                        if (originalPixel) {
                          context.putImageData(originalPixel, 0, 0);
                        }
                      }
                    };
                  }

                  Object.defineProperty(window, '__flipbotFingerprintLab', {
                    configurable: false,
                    enumerable: false,
                    writable: false,
                    value: Object.freeze({
                      active: true,
                      scope: 'local-lab-only',
                      synthetic: true,
                      changedSurfaces: Object.freeze([
                        'navigator.platform',
                        'navigator.hardwareConcurrency',
                        'navigator.deviceMemory',
                        'navigator.language',
                        'navigator.languages',
                        'navigator.maxTouchPoints',
                        'screen.*',
                        'devicePixelRatio',
                        'WebGL getParameter',
                        'canvas.toDataURL'
                      ])
                    })
                  });
                })();
                """.formatted(profileJson);
    }
}
