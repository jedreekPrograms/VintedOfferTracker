package pl.flipbot.playwright.negotiation;

import org.junit.Test;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.model.BotConfigurationDto;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Locks the business rule: current native-filter evidence is trusted as
 * model identity, but persisted history without current scan proof is deferred.
 */
public class NewNegotiationProvenanceTest {

    @Test
    public void nativeModelAcceptsCurrentScanEvenWhenSellerTitleLooksDifferent() {
        BotConfigurationDto configuration = configuration("VINTED_MODEL");
        configuration.setModel("Galaxy S25");

        ListingResponseDto listing = listing("123456", "Samsung Galaxy S24 Ultra 256GB");

        assertTrue(NewNegotiationProcessor.hasCurrentExactModelProof(
                listing, configuration, Set.of("123456", "987654")
        ));
    }

    @Test
    public void nativeModelDefersBacklogAbsentFromCurrentScan() {
        BotConfigurationDto configuration = configuration("VINTED_MODEL");
        ListingResponseDto historical = listing("123456", "Galaxy S25");

        assertFalse(NewNegotiationProcessor.hasCurrentExactModelProof(
                historical, configuration, Set.of("987654")
        ));
        assertFalse(NewNegotiationProcessor.hasCurrentExactModelProof(
                historical, configuration, Set.of()
        ));
        assertFalse(NewNegotiationProcessor.hasCurrentExactModelProof(
                historical, configuration, null
        ));
    }

    @Test
    public void searchQueryNeverTreatsCurrentScanAsExactModelProof() {
        BotConfigurationDto configuration = configuration("SEARCH_QUERY");
        configuration.setSearchQuery("Samsung Galaxy S25");
        assertFalse(NewNegotiationProcessor.hasCurrentExactModelProof(
                listing("123456", "Galaxy S25"),
                configuration, Set.of("123456")
        ));
    }

    @Test
    public void nullOrMissingListingIdentityNeverEstablishesProof() {
        BotConfigurationDto configuration = configuration("VINTED_MODEL");
        assertFalse(NewNegotiationProcessor.hasCurrentExactModelProof(
                null, configuration, Set.of("123456")
        ));
        assertFalse(NewNegotiationProcessor.hasCurrentExactModelProof(
                listing(null, "Galaxy S25"), configuration, Set.of("123456")
        ));
    }

    @Test
    public void legacyMissingModePreservesDefaultNativeFilterSemantics() {
        ListingResponseDto listing = listing("123456", "Unknown");
        for (String mode : new String[]{null, "", "   ", "vinted_model", " VINTED_MODEL "}) {
            BotConfigurationDto configuration = configuration(mode);
            assertTrue(NewNegotiationProcessor.usesExactVintedModelFilter(configuration));
            assertTrue(NewNegotiationProcessor.hasCurrentExactModelProof(
                    listing, configuration, Set.of("123456")
            ));
        }

        assertFalse(NewNegotiationProcessor.usesExactVintedModelFilter(null));
        assertFalse(NewNegotiationProcessor.hasCurrentExactModelProof(
                listing, null, Set.of("123456")
        ));
        assertFalse(NewNegotiationProcessor.usesExactVintedModelFilter(
                configuration("OTHER_MODE")
        ));
    }

    private static BotConfigurationDto configuration(String mode) {
        BotConfigurationDto configuration = new BotConfigurationDto();
        configuration.setTargetMode(mode);
        return configuration;
    }

    private static ListingResponseDto listing(String marketplaceId, String title) {
        return new ListingResponseDto(
                42L,
                marketplaceId,
                title,
                "https://www.vinted.pl/items/" + marketplaceId,
                new BigDecimal("2000"),
                new BigDecimal("2000"),
                1,
                false,
                null,
                null,
                "DISCOVERED",
                null
        );
    }
}
