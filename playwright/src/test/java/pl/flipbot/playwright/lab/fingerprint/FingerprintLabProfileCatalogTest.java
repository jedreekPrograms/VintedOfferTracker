package pl.flipbot.playwright.lab.fingerprint;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FingerprintLabProfileCatalogTest {

    @Test
    public void exposesCoherentNamedProfiles() {
        assertTrue(FingerprintLabProfileCatalog.ids().contains(
                "windows-desktop-pl"
        ));
        assertTrue(FingerprintLabProfileCatalog.ids().contains(
                "windows-laptop-pl"
        ));
        assertTrue(FingerprintLabProfileCatalog.ids().contains(
                "windows-desktop-en"
        ));

        for (String id : FingerprintLabProfileCatalog.ids()) {
            FingerprintLabProfile profile =
                    FingerprintLabProfileCatalog.byId(id);

            assertFalse(profile.platform().isBlank());
            assertFalse(profile.languages().isEmpty());
            assertEquals(profile.locale(), profile.languages().getFirst());
            assertTrue(profile.availWidth() <= profile.screenWidth());
            assertTrue(profile.availHeight() <= profile.screenHeight());
            assertTrue(profile.hardwareConcurrency() > 0);
            assertTrue(profile.deviceMemoryGb() > 0);
            assertTrue(profile.deviceScaleFactor() > 0);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnknownProfile() {
        FingerprintLabProfileCatalog.byId("does-not-exist");
    }

    @Test
    public void blankProfileFallsBackToDefault() {
        assertEquals(
                FingerprintLabProfileCatalog.defaultProfile(),
                FingerprintLabProfileCatalog.byId("   ")
        );
    }
}
