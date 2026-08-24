package pl.flipbot.playwright.probe;

import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.assertTrue;

public class PriceProbeExecutionContractTest {

    @Test
    public void assignmentRequiresExplicitPlnMessage() {
        PriceProbeAssignmentDto assignment = new PriceProbeAssignmentDto(
                1L,
                2L,
                "123",
                "Samsung",
                "https://www.vinted.pl/items/123",
                new BigDecimal("1300"),
                new BigDecimal("1050"),
                "Mogę zaproponować 1050 PLN.",
                1,
                15
        );

        assertTrue(assignment.message().contains("PLN"));
        assertTrue(assignment.probePrice().compareTo(assignment.referenceOfferPrice()) < 0);
    }
}
