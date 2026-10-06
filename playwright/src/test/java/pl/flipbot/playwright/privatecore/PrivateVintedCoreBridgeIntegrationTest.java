package pl.flipbot.playwright.privatecore;

import org.junit.Assume;
import org.junit.Test;

public class PrivateVintedCoreBridgeIntegrationTest {

    @Test
    public void privateCoreApiIsCompatibleWhenInstalled() {
        /*
         * Public CI intentionally does not have access to the private JAR.
         * The private-repository integration workflow installs it locally and
         * activates the Maven profile before running this same test suite.
         */
        Assume.assumeTrue(
                "Private core is not installed in this environment.",
                PrivateVintedCoreBridge.isAvailable()
        );

        PrivateVintedCoreBridge.requireAvailable();
    }
}
