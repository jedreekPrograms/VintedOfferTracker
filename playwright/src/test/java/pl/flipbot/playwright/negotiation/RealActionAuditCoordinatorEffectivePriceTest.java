package pl.flipbot.playwright.negotiation;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import pl.flipbot.playwright.api.audit.RealActionAuditClient;
import pl.flipbot.playwright.api.audit.dto.RealActionAuditRequestDto;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.model.BotDetailsDto;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class RealActionAuditCoordinatorEffectivePriceTest {

    @Test
    public void ambiguousAuditUsesPreparedEffectivePriceInsteadOfMainConfigPrice() {
        BotContext context = mock(BotContext.class);
        BotDetailsDto bot = new BotDetailsDto();
        bot.setId(10L);
        when(context.getBot()).thenReturn(bot);

        ListingResponseDto listing = mock(ListingResponseDto.class);
        when(listing.id()).thenReturn(3914L);
        when(listing.listingId()).thenReturn("10034864145");

        RealActionAuditClient client = mock(RealActionAuditClient.class);
        RealActionAuditCoordinator coordinator =
                new RealActionAuditCoordinator(context, client);

        UUID requestId = UUID.randomUUID();
        BigDecimal effectivePrice = new BigDecimal("1070.00");

        coordinator.recordAmbiguousBestEffort(
                listing,
                "NEXT_STEP",
                2,
                effectivePrice,
                requestId,
                new IllegalStateException("confirmation timeout")
        );

        ArgumentCaptor<RealActionAuditRequestDto> captor =
                ArgumentCaptor.forClass(RealActionAuditRequestDto.class);

        verify(client).record(
                eq(10L),
                eq(3914L),
                captor.capture()
        );

        assertEquals(effectivePrice, captor.getValue().offerPrice());
        assertEquals("AMBIGUOUS", captor.getValue().outcome());
        assertEquals(requestId, captor.getValue().requestId());
    }
}
