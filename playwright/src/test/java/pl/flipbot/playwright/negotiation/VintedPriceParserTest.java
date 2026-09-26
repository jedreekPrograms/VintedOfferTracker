package pl.flipbot.playwright.negotiation;

import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;

public class VintedPriceParserTest {

    @Test
    public void parsesPolishPriceWithNbspAndCurrency() {
        assertEquals(
                new BigDecimal("1900.00"),
                VintedPriceParser.parse("1900,00\u00A0zł")
        );
    }

    @Test
    public void parsesThousandsAndDecimalComma() {
        assertEquals(
                new BigDecimal("2098.43"),
                VintedPriceParser.parse("2 098,43 zł")
        );
    }
}
