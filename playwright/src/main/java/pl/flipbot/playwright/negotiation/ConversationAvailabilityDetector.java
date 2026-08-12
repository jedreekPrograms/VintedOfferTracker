package pl.flipbot.playwright.negotiation;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.context.BotContext;

@Slf4j
@RequiredArgsConstructor
public class ConversationAvailabilityDetector {

    private static final String STATUS_MESSAGE_TEST_ID =
            "conversation-message--status-message";

    private static final String UNAVAILABLE_TITLE =
            "Przedmiot jest niedostępny";

    private static final String UNAVAILABLE_DESCRIPTION =
            "Przedmiot został sprzedany lub usunięty";

    private final BotContext context;


    public boolean isUnavailable(
            ListingResponseDto listing
    ) {

        if (
                listing == null
                        || listing.conversationId() == null
                        || listing.conversationId().isBlank()
        ) {

            return false;
        }


        Page page =
                context.getPage();


        if (!isExpectedConversationOpen(
                page,
                listing.conversationId()
        )) {

            /*
             * Nie wolno oznaczyć listingu jako UNAVAILABLE na podstawie
             * bannera pochodzącego z innej rozmowy.
             */
            return false;
        }


        try {

            Locator statusMessages =
                    page.getByTestId(
                            STATUS_MESSAGE_TEST_ID
                    );


            int count =
                    statusMessages.count();


            for (int index = 0; index < count; index++) {

                Locator statusMessage =
                        statusMessages.nth(
                                index
                        );


                if (!statusMessage.isVisible()) {

                    continue;
                }


                String text =
                        normalize(
                                statusMessage.innerText()
                        );


                if (
                        text.contains(
                                UNAVAILABLE_TITLE
                        )
                                || text.contains(
                                UNAVAILABLE_DESCRIPTION
                        )
                ) {

                    log.warn(
                            "[AVAILABILITY] Vinted reports marketplace listing {} "
                                    + "as unavailable in conversation {}. "
                                    + "Status message: {}",
                            listing.listingId(),
                            listing.conversationId(),
                            text
                    );


                    return true;
                }
            }


            return false;

        } catch (Exception exception) {

            /*
             * Detektor dostępności jest guardem bezpieczeństwa.
             * Jeżeli sam odczyt DOM nie zadziała, nie zgadujemy statusu.
             */
            log.debug(
                    "[AVAILABILITY] Could not inspect availability status "
                            + "for marketplace listing {} in conversation {}.",
                    listing.listingId(),
                    listing.conversationId()
            );


            log.trace(
                    "[AVAILABILITY] Full availability detection exception.",
                    exception
            );


            return false;
        }
    }


    private boolean isExpectedConversationOpen(
            Page page,
            String conversationId
    ) {

        String currentUrl =
                page.url();


        if (
                currentUrl == null
                        || currentUrl.isBlank()
        ) {

            return false;
        }


        return currentUrl.contains(
                "/inbox/" + conversationId
        );
    }


    private String normalize(
            String value
    ) {

        if (value == null) {

            return "";
        }


        return value.replaceAll(
                        "\\s+",
                        " "
                )
                .trim();
    }
}