package pl.flipbot.playwright.negotiation;

import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ConversationOfferModalPriceInspectorTest {

    @Test
    public void extractsItemPriceAndIgnoresBuyerProtectionTotal() {
        String modalText = """
                Zaproponuj cenę
                iPhone 15 pro
                Cena przedmiotu: 1900,00\u00A0zł
                1805,00 zł
                5% zniżki
                1710,00 zł
                10% zniżki
                Własna
                Wpisz cenę
                1997,90\u00A0zł, w tym opłata za Ochronę Kupujących
                Zaproponuj
                """;

        assertEquals(
                new BigDecimal("1900.00"),
                ConversationOfferModalPriceInspector
                        .parseItemPriceFromModalText(modalText)
                        .orElseThrow()
        );
    }

    @Test
    public void missingItemPriceLabelReturnsEmpty() {
        assertTrue(
                ConversationOfferModalPriceInspector
                        .parseItemPriceFromModalText(
                                "1997,90 zł, w tym opłata za Ochronę Kupujących"
                        )
                        .isEmpty()
        );
    }
}
