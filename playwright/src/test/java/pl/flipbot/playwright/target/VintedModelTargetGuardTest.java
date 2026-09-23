package pl.flipbot.playwright.target;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VintedModelTargetGuardTest {

    private final VintedModelTargetGuard guard =
            new VintedModelTargetGuard();

    @Test
    public void rejectsFlip4WhenBotTargetsS25() {
        assertTrue(
                guard.findConclusiveMismatch(
                        "Galaxy S25",
                        "Samsung Galaxy Z Flip 4"
                ).isPresent()
        );
    }

    @Test
    public void rejectsFold5WhenBotTargetsS25() {
        assertTrue(
                guard.findConclusiveMismatch(
                        "Galaxy S25",
                        "Samsung Galaxy Z Fold5"
                ).isPresent()
        );
    }

    @Test
    public void rejectsGalaxyTabS9FePlusWhenBotTargetsS25() {
        assertTrue(
                guard.findConclusiveMismatch(
                        "Galaxy S25",
                        "Galaxy Tab S9 FE+"
                ).isPresent()
        );
    }

    @Test
    public void rejectsGalaxyTabActive3WithoutPhoneModelKey() {
        assertTrue(
                guard.findConclusiveMismatch(
                        "Galaxy S25",
                        "Samsung Galaxy Tab Active 3"
                ).isPresent()
        );
    }

    @Test
    public void rejectsGalaxyTabSWithoutGenerationKey() {
        assertTrue(
                guard.findConclusiveMismatch(
                        "Galaxy S25",
                        "Samsung Galaxy Tab S"
                ).isPresent()
        );
    }

    @Test
    public void rejectsDifferentSSeriesModel() {
        assertTrue(
                guard.findConclusiveMismatch(
                        "Galaxy S25",
                        "Samsung Galaxy S24 256 GB"
                ).isPresent()
        );
    }

    @Test
    public void rejectsUnexpectedVariant() {
        assertTrue(
                guard.findConclusiveMismatch(
                        "Galaxy S25",
                        "Samsung Galaxy S25 Ultra"
                ).isPresent()
        );
    }

    @Test
    public void positivelyProvesMatchingS25Title() {
        assertTrue(
                guard.provesConfiguredModel(
                        "Galaxy S25",
                        "Telefon Samsung S25 128GB"
                )
        );
    }

    @Test
    public void genericTitleIsNeitherPositiveProofNorMismatch() {
        assertFalse(
                guard.findConclusiveMismatch(
                        "Galaxy S25",
                        "Tablet z wyświetlaczem do wymiany"
                ).isPresent()
        );
        assertFalse(
                guard.provesConfiguredModel(
                        "Galaxy S25",
                        "Tablet z wyświetlaczem do wymiany"
                )
        );
    }

    @Test
    public void targetPlusRequiresPlusEvidence() {
        assertFalse(
                guard.provesConfiguredModel(
                        "Galaxy S25+",
                        "Samsung Galaxy S25"
                )
        );
        assertTrue(
                guard.provesConfiguredModel(
                        "Galaxy S25+",
                        "Samsung Galaxy S25+ 256GB"
                )
        );
    }

    @Test
    public void baseS25RejectsPlusEvidence() {
        assertTrue(
                guard.findConclusiveMismatch(
                        "Galaxy S25",
                        "Samsung Galaxy S25+ 256GB"
                ).isPresent()
        );
        assertFalse(
                guard.provesConfiguredModel(
                        "Galaxy S25",
                        "Samsung Galaxy S25+ 256GB"
                )
        );
    }

    @Test
    public void ignoresSamsungTechnicalProductCodeAsModelEvidence() {
        assertFalse(
                guard.findConclusiveMismatch(
                        "Galaxy S25",
                        "Samsung SM-S931 12/128 GB"
                ).isPresent()
        );
        assertFalse(
                guard.provesConfiguredModel(
                        "Galaxy S25",
                        "Samsung SM-S931 12/128 GB"
                )
        );
    }
}
