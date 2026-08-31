package pl.flipbot.playwright.target;

import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class VintedItemIdentityReaderTest {

    @Test
    public void readsPolishBrandAndModelFromSeparateLines() {
        List<String> lines = VintedItemIdentityReader.toMeaningfulLines(
                "Tablet z wyświetlaczem do wymiany\n"
                        + "Dobry · Samsung\n"
                        + "766,00 zł\n"
                        + "Marka\n"
                        + "Samsung\n"
                        + "Model\n"
                        + "Galaxy Tab S9 FE+\n"
                        + "Pamięć wbudowana\n"
                        + "256 GB"
        );

        assertEquals(
                "Samsung",
                VintedItemIdentityReader.readLabelledValue(
                        lines,
                        Set.of("marka", "brand")
                )
        );
        assertEquals(
                "Galaxy Tab S9 FE+",
                VintedItemIdentityReader.readLabelledValue(
                        lines,
                        Set.of("model")
                )
        );
    }

    @Test
    public void readsInlineLabelledValues() {
        List<String> lines = List.of(
                "Brand: Samsung",
                "Model Galaxy S25"
        );

        assertEquals(
                "Samsung",
                VintedItemIdentityReader.readLabelledValue(
                        lines,
                        Set.of("marka", "brand")
                )
        );
        assertEquals(
                "Galaxy S25",
                VintedItemIdentityReader.readLabelledValue(
                        lines,
                        Set.of("model")
                )
        );
    }
}
