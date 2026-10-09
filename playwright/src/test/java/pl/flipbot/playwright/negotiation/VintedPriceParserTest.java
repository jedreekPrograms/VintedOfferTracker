package pl.flipbot.playwright.negotiation;

import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class VintedPriceParserTest {

    @Test
    public void parsesPolishPriceWithNbspAndCurrency() {
        assertEquals(
                new BigDecimal("1900.00"),
                VintedPriceParser.parse("1900,00\u00A0zł")
        );
    }

    @Test
    public void parsesPolishThousandsCounteroffer() {
        assertEquals(
                new BigDecimal("1275.00"),
                VintedPriceParser.parse("1 275,00\u00A0zł")
        );
    }

    @Test
    public void parsesThousandsAndDecimalComma() {
        assertEquals(
                new BigDecimal("2098.43"),
                VintedPriceParser.parse("2 098,43 zł")
        );
    }

    @Test
    public void confirmationParsersRetainEuroAndPolishDecimalConventions() {
        assertEquals(
                new BigDecimal("1234.56"),
                VintedPriceParser.parseFirstOfferConfirmation("1.234,56 zł")
        );
        assertEquals(
                new BigDecimal("1234.56"),
                VintedPriceParser.parseNextStepConfirmation("1,234.56 PLN")
        );
        assertEquals(
                new BigDecimal("1250.00"),
                VintedPriceParser.parseNextStepConfirmation("1\u202F250,00 zł")
        );
    }

    @Test
    public void confirmationParsersRetainSingleSeparatorAndPlainPriceBehavior() {
        assertEquals(
                new BigDecimal("19.75"),
                VintedPriceParser.parseFirstOfferConfirmation("19,75 zł")
        );
        assertEquals(
                new BigDecimal("999"),
                VintedPriceParser.parseNextStepConfirmation("999 PLN")
        );
        assertEquals(
                new BigDecimal("2200.10"),
                VintedPriceParser.parseNextStepConfirmation("2 200.10")
        );
    }

    @Test
    public void firstOfferConfirmationRetainsLegacyAcceptanceOfZeroAndNegativeValue() {
        assertEquals(
                new BigDecimal("0"),
                VintedPriceParser.parseFirstOfferConfirmation("0 zł")
        );
        assertEquals(
                new BigDecimal("-10.50"),
                VintedPriceParser.parseFirstOfferConfirmation("-10,50 zł")
        );
    }

    @Test
    public void nextStepConfirmationRejectsZeroAndNegativePrices() {
        IllegalArgumentException zero = assertThrows(
                IllegalArgumentException.class,
                () -> VintedPriceParser.parseNextStepConfirmation("0 zł")
        );
        assertEquals("Price must be greater than zero: 0 zł", zero.getMessage());
        assertThrows(
                IllegalArgumentException.class,
                () -> VintedPriceParser.parseNextStepConfirmation("-10,50 zł")
        );
    }

    @Test
    public void originalStrictParserRemainsPositiveOnly() {
        assertThrows(
                IllegalArgumentException.class,
                () -> VintedPriceParser.parse("0 zł")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> VintedPriceParser.parse("-5.00")
        );
        assertEquals(
                new BigDecimal("1550.00"),
                VintedPriceParser.parse("1\u00A0550,00 PLN")
        );
    }

    @Test
    public void missingNumericEvidencePreservesDistinctLegacyErrors() {
        // Legacy BigDecimal("") throws NumberFormatException, whose message
        // can be null on Java 21; only the exception type is guaranteed.
        assertThrows(
                NumberFormatException.class,
                () -> VintedPriceParser.parseFirstOfferConfirmation("PLN")
        );

        IllegalArgumentException next = assertThrows(
                IllegalArgumentException.class,
                () -> VintedPriceParser.parseNextStepConfirmation("PLN")
        );
        assertEquals("Price contains no numeric value: PLN", next.getMessage());

        IllegalArgumentException strict = assertThrows(
                IllegalArgumentException.class,
                () -> VintedPriceParser.parse("PLN")
        );
        assertEquals("Price text contains no numeric value: PLN", strict.getMessage());
    }

    @Test
    public void allModesRejectBlankInputWithSameError() {
        assertEquals(
                "Price text cannot be blank",
                assertThrows(
                        IllegalArgumentException.class,
                        () -> VintedPriceParser.parseFirstOfferConfirmation("  ")
                ).getMessage()
        );
        assertEquals(
                "Price text cannot be blank",
                assertThrows(
                        IllegalArgumentException.class,
                        () -> VintedPriceParser.parseNextStepConfirmation(null)
                ).getMessage()
        );
        assertEquals(
                "Price text cannot be blank",
                assertThrows(
                        IllegalArgumentException.class,
                        () -> VintedPriceParser.parse(null)
                ).getMessage()
        );
    }

}
