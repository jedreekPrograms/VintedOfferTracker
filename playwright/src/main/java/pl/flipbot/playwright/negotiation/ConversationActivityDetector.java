package pl.flipbot.playwright.negotiation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.context.BotContext;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class ConversationActivityDetector {

    private static final DateTimeFormatter
            VINTED_TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern(
                    "d.M.yyyy, HH:mm:ss"
            );

    private static final String INSPECTION_SCRIPT =
            """
            () => {
                const content =
                    document.querySelector(
                        '[data-testid="conversation-content"]'
                    );

                if (!content) {
                    return {
                        inspectionSucceeded: false,
                        latestOwnOfferFound: false,
                        sellerMessageAfterLatestOwnOffer: false,
                        latestSellerMessageText: null,
                        latestSellerMessageTimestamp: null,
                        readIndicatorAfterLatestOwnOffer: false
                    };
                }

                const ownOffers =
                    Array.from(
                        content.querySelectorAll(
                            '[data-testid="offer-request-current-price-label"]'
                        )
                    );

                const latestOwnOffer =
                    ownOffers.length > 0
                        ? ownOffers[ownOffers.length - 1]
                        : null;

                const isAfter =
                    (referenceNode, candidateNode) => {

                        if (!referenceNode || !candidateNode) {
                            return false;
                        }

                        return Boolean(
                            referenceNode.compareDocumentPosition(
                                candidateNode
                            )
                            & Node.DOCUMENT_POSITION_FOLLOWING
                        );
                    };

                const conversationMessages =
                    Array.from(
                        content.querySelectorAll(
                            '[data-testid="conversation-message"]'
                        )
                    );

                const sellerMessages =
                    conversationMessages.filter(
                        message => {

                            const labelledMessage =
                                message.querySelector(
                                    '[aria-label^="Message from "]'
                                );

                            if (!labelledMessage) {
                                return false;
                            }

                            const ariaLabel =
                                labelledMessage.getAttribute(
                                    'aria-label'
                                ) || '';

                            return !ariaLabel.startsWith(
                                'Message from me:'
                            );
                        }
                    );

                const sellerMessagesAfterLatestOwnOffer =
                    latestOwnOffer
                        ? sellerMessages.filter(
                            message =>
                                isAfter(
                                    latestOwnOffer,
                                    message
                                )
                        )
                        : [];

                const latestSellerMessage =
                    sellerMessagesAfterLatestOwnOffer.length > 0
                        ? sellerMessagesAfterLatestOwnOffer[
                            sellerMessagesAfterLatestOwnOffer.length - 1
                        ]
                        : null;

                const latestSellerMessageText =
                    latestSellerMessage
                        ? (
                            latestSellerMessage.textContent || ''
                        ).trim()
                        : null;

                const findMessageTimestamp =
                    message => {

                        if (!message) {
                            return null;
                        }

                        /*
                         * W HTML Vinted timestamp wiadomości znajduje się
                         * bezpośrednio PRZED kontenerem wiadomości:
                         *
                         * <div>...<span title="10.08.2026, 20:22:50">...</span></div>
                         * <div class="...conversation-message-container...">
                         *     <div data-testid="conversation-message">...</div>
                         * </div>
                         */
                        const messageContainer =
                            message.closest(
                                '[class*="conversation-message-container"]'
                            );

                        if (!messageContainer) {
                            return null;
                        }

                        let sibling =
                            messageContainer.previousElementSibling;

                        /*
                         * Zwykle timestamp jest w pierwszym poprzednim
                         * elemencie. Dopuszczamy jednak kilka elementów
                         * technicznych między timestampem i wiadomością.
                         */
                        for (
                            let i = 0;
                            sibling && i < 4;
                            i++
                        ) {

                            const timestampElement =
                                sibling.matches('[title]')
                                    ? sibling
                                    : sibling.querySelector(
                                        '[title]'
                                    );

                            if (timestampElement) {

                                const title =
                                    timestampElement.getAttribute(
                                        'title'
                                    );

                                if (title) {
                                    return title.trim();
                                }
                            }

                            /*
                             * Nie cofamy się przez wcześniejszą wiadomość,
                             * bo moglibyśmy przypisać jej timestamp do
                             * aktualnej wiadomości sprzedającego.
                             */
                            if (
                                sibling.querySelector(
                                    '[data-testid="conversation-message"], '
                                    + '[data-testid="offer-request-current-price-label"], '
                                    + '[data-testid="offer-current-price-label"]'
                                )
                            ) {
                                break;
                            }

                            sibling =
                                sibling.previousElementSibling;
                        }

                        return null;
                    };

                const latestSellerMessageTimestamp =
                    findMessageTimestamp(
                        latestSellerMessage
                    );

                const readIndicators =
                    Array.from(
                        content.querySelectorAll(
                            '[data-testid="message-read-indicator"]'
                        )
                    );

                const readIndicatorAfterLatestOwnOffer =
                    latestOwnOffer
                        ? readIndicators.some(
                            indicator =>
                                isAfter(
                                    latestOwnOffer,
                                    indicator
                                )
                        )
                        : false;

                return {
                    inspectionSucceeded: true,
                    latestOwnOfferFound:
                        latestOwnOffer !== null,
                    sellerMessageAfterLatestOwnOffer:
                        sellerMessagesAfterLatestOwnOffer.length > 0,
                    latestSellerMessageText:
                        latestSellerMessageText,
                    latestSellerMessageTimestamp:
                        latestSellerMessageTimestamp,
                    readIndicatorAfterLatestOwnOffer:
                        readIndicatorAfterLatestOwnOffer
                };
            }
            """;

    private final BotContext context;


    public ConversationActivitySnapshot inspect() {

        try {

            Object rawResult =
                    context.getPage()
                            .evaluate(
                                    INSPECTION_SCRIPT
                            );


            if (
                    !(rawResult
                            instanceof Map<?, ?> result)
            ) {

                log.warn(
                        "[CONVERSATION ACTIVITY] Browser returned "
                                + "an unexpected activity inspection result."
                );

                return ConversationActivitySnapshot
                        .unavailable();
            }


            String sellerMessageTimestamp =
                    readNullableString(
                            result,
                            "latestSellerMessageTimestamp"
                    );


            return new ConversationActivitySnapshot(
                    readBoolean(
                            result,
                            "inspectionSucceeded"
                    ),
                    readBoolean(
                            result,
                            "latestOwnOfferFound"
                    ),
                    readBoolean(
                            result,
                            "sellerMessageAfterLatestOwnOffer"
                    ),
                    readNullableString(
                            result,
                            "latestSellerMessageText"
                    ),
                    parseVintedTimestamp(
                            sellerMessageTimestamp
                    ),
                    readBoolean(
                            result,
                            "readIndicatorAfterLatestOwnOffer"
                    )
            );

        } catch (Exception exception) {

            /*
             * Ten detektor jest dodatkową warstwą obserwacyjną.
             * Jego błąd nie może zatrzymać istniejącej, działającej
             * logiki negocjacji.
             */
            log.warn(
                    "[CONVERSATION ACTIVITY] Could not inspect "
                            + "seller-message/read activity: {}",
                    getFriendlyErrorMessage(
                            exception
                    )
            );

            log.trace(
                    "[CONVERSATION ACTIVITY] Full activity "
                            + "inspection exception.",
                    exception
            );


            return ConversationActivitySnapshot
                    .unavailable();
        }
    }


    LocalDateTime parseVintedTimestamp(
            String rawTimestamp
    ) {

        if (
                rawTimestamp == null
                        || rawTimestamp.isBlank()
        ) {

            return null;
        }


        try {

            return LocalDateTime.parse(
                    rawTimestamp,
                    VINTED_TIMESTAMP_FORMAT
            );

        } catch (DateTimeParseException exception) {

            log.warn(
                    "[CONVERSATION ACTIVITY] Could not parse Vinted "
                            + "seller-message timestamp: {}",
                    rawTimestamp
            );

            log.trace(
                    "[CONVERSATION ACTIVITY] Full timestamp "
                            + "parse exception.",
                    exception
            );


            return null;
        }
    }


    private boolean readBoolean(
            Map<?, ?> result,
            String key
    ) {

        return Boolean.TRUE.equals(
                result.get(
                        key
                )
        );
    }


    private String readNullableString(
            Map<?, ?> result,
            String key
    ) {

        Object value =
                result.get(
                        key
                );


        if (value == null) {

            return null;
        }


        String text =
                value.toString()
                        .trim();


        if (text.isBlank()) {

            return null;
        }


        return text;
    }


    private String getFriendlyErrorMessage(
            Throwable exception
    ) {

        if (exception == null) {

            return "Unknown error";
        }


        String message =
                exception.getMessage();


        if (
                message == null
                        || message.isBlank()
        ) {

            return exception
                    .getClass()
                    .getSimpleName();
        }


        int firstLineEnd =
                message.indexOf('\n');


        if (
                firstLineEnd > 0
        ) {

            return message
                    .substring(
                            0,
                            firstLineEnd
                    )
                    .trim();
        }


        return message.trim();
    }
}
