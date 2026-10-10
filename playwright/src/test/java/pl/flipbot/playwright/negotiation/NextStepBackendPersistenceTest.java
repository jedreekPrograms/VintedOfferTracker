package pl.flipbot.playwright.negotiation;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import pl.flipbot.playwright.api.listing.ListingClient;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.api.listing.dto.UpdateListingRequestDto;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.model.BotDetailsDto;
import pl.flipbot.playwright.model.NegotiationStepDto;

import java.math.BigDecimal;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class NextStepBackendPersistenceTest {
    private final BotContext context = mock(BotContext.class);
    private final ListingClient client = mock(ListingClient.class);
    private final NextStepBackendPersistence persistence = new NextStepBackendPersistence(context, client);

    @Test
    public void persistsActuallyDisplayedPriceAndPreservesCanonicalConversationFields() {
        ListingResponseDto before = listing("NEGOTIATING", 3, "chat-1");
        ListingResponseDto response = listing("NEGOTIATING", 4, "chat-1");
        setup(response);
        NegotiationStepDto step = step(4, "1250.00");

        assertSame(response, persistence.markNextStepStarted(
                before, step, new BigDecimal("1248.00")));

        ArgumentCaptor<UpdateListingRequestDto> request =
                ArgumentCaptor.forClass(UpdateListingRequestDto.class);
        verify(client, times(1)).updateListing(eq(9L), eq(100L), request.capture());
        assertEquals("NEGOTIATING", request.getValue().status());
        assertEquals(new BigDecimal("1248.00"), request.getValue().currentPrice());
        assertEquals(4, request.getValue().currentStep());
        assertEquals(Boolean.TRUE, request.getValue().awaitingSellerResponse());
        assertEquals("chat-1", request.getValue().conversationId());
        assertEquals("https://www.vinted.pl/inbox/chat-1", request.getValue().conversationUrl());
    }

    @Test
    public void rejectsBackendResponseWithUnexpectedStatus() {
        setup(listing("DISCOVERED", 4, "chat-1"));
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> persistence.markNextStepStarted(
                        listing("NEGOTIATING", 3, "chat-1"), step(4, "1250.00"),
                        new BigDecimal("1248.00")));
        assertTrue(exception.getMessage().contains("unexpected status"));
    }

    @Test
    public void rejectsBackendResponseWithWrongStep() {
        setup(listing("NEGOTIATING", 3, "chat-1"));
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> persistence.markNextStepStarted(
                        listing("NEGOTIATING", 3, "chat-1"), step(4, "1250.00"),
                        new BigDecimal("1248.00")));
        assertTrue(exception.getMessage().contains("unexpected current step"));
    }

    @Test
    public void rejectsBackendResponseWithAnotherConversation() {
        setup(listing("NEGOTIATING", 4, "unrelated-chat"));
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> persistence.markNextStepStarted(
                        listing("NEGOTIATING", 3, "chat-1"), step(4, "1250.00"),
                        new BigDecimal("1248.00")));
        assertTrue(exception.getMessage().contains("unexpected conversation ID"));
    }

    private void setup(ListingResponseDto returned) {
        BotDetailsDto bot = new BotDetailsDto();
        bot.setId(9L);
        when(context.getBot()).thenReturn(bot);
        when(client.updateListing(eq(9L), eq(100L), any(UpdateListingRequestDto.class)))
                .thenReturn(returned);
    }

    private static ListingResponseDto listing(String status, int currentStep, String conversation) {
        return new ListingResponseDto(
                100L, "item-123", "Galaxy S25", "https://www.vinted.pl/items/item-123",
                new BigDecimal("2000.00"), new BigDecimal("1200.00"),
                currentStep, true, conversation, "https://www.vinted.pl/inbox/chat-1",
                status, null
        );
    }

    private static NegotiationStepDto step(int number, String configuredPrice) {
        NegotiationStepDto step = new NegotiationStepDto();
        step.setStepNumber(number);
        step.setOfferPrice(new BigDecimal(configuredPrice));
        return step;
    }
}
