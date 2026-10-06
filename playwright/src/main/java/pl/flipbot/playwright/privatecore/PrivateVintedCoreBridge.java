package pl.flipbot.playwright.privatecore;

import com.microsoft.playwright.Page;
import pl.flipbot.playwright.negotiation.ConversationActivitySnapshot;
import pl.flipbot.playwright.negotiation.NegotiationConversationSnapshot;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Thin public boundary to the private Playwright core.
 *
 * The public repository intentionally contains no Vinted DOM inspection
 * implementation. Runtime implementations are loaded from
 * pl.flipbot:flipbot-playwright-core and installed locally by
 * bootstrap-private-core.ps1.
 */
public final class PrivateVintedCoreBridge {

    private static final String CONVERSATION_INSPECTOR =
            "pl.flipbot.core.vinted.ConversationInspector";

    private static final String ACTIVITY_INSPECTOR =
            "pl.flipbot.core.vinted.ConversationActivityInspector";

    private PrivateVintedCoreBridge() {
    }

    public static boolean isAvailable() {
        try {
            Class.forName(CONVERSATION_INSPECTOR);
            Class.forName(ACTIVITY_INSPECTOR);
            return true;
        } catch (ClassNotFoundException exception) {
            return false;
        }
    }

    public static void requireAvailable() {
        try {
            Class<?> conversationInspector =
                    requireClass(CONVERSATION_INSPECTOR);
            Method conversationMethod =
                    conversationInspector.getMethod(
                            "inspect",
                            Page.class,
                            String.class,
                            String.class,
                            BigDecimal.class,
                            Runnable.class
                    );

            Class<?> conversationResult =
                    conversationMethod.getReturnType();
            conversationResult.getMethod("result");
            conversationResult.getMethod(
                    "sellerCounterOfferPrice"
            );
            conversationResult.getMethod("rawStatus");

            Class<?> activityInspector =
                    requireClass(ACTIVITY_INSPECTOR);
            Method activityMethod =
                    activityInspector.getMethod(
                            "inspect",
                            Page.class
                    );

            Class<?> activityResult =
                    activityMethod.getReturnType();
            activityResult.getMethod("inspectionSucceeded");
            activityResult.getMethod("latestOwnOfferFound");
            activityResult.getMethod(
                    "sellerMessageAfterLatestOwnOffer"
            );
            activityResult.getMethod(
                    "latestSellerMessageText"
            );
            activityResult.getMethod(
                    "latestSellerMessageAt"
            );
            activityResult.getMethod(
                    "readIndicatorAfterLatestOwnOffer"
            );
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "Private Playwright core is present but its API is incompatible with this public runtime. "
                            + "Run .\\bootstrap-private-core.ps1 again after pulling both repositories.",
                    exception
            );
        }
    }

    public static NegotiationConversationSnapshot inspectConversation(
            Page page,
            String marketplaceListingId,
            String conversationId,
            BigDecimal capturedOriginalPrice,
            Runnable beforePoll
    ) {
        try {
            Class<?> inspectorType =
                    requireClass(CONVERSATION_INSPECTOR);
            Object inspector =
                    inspectorType.getDeclaredConstructor().newInstance();

            Method inspect = inspectorType.getMethod(
                    "inspect",
                    Page.class,
                    String.class,
                    String.class,
                    BigDecimal.class,
                    Runnable.class
            );

            Object result = inspect.invoke(
                    inspector,
                    page,
                    marketplaceListingId,
                    conversationId,
                    capturedOriginalPrice,
                    beforePoll
            );

            Method resultMethod =
                    result.getClass().getMethod("result");
            Method priceMethod =
                    result.getClass().getMethod(
                            "sellerCounterOfferPrice"
                    );
            Method rawStatusMethod =
                    result.getClass().getMethod("rawStatus");

            String resultName =
                    ((Enum<?>) resultMethod.invoke(result)).name();
            BigDecimal sellerPrice =
                    (BigDecimal) priceMethod.invoke(result);
            String rawStatus =
                    (String) rawStatusMethod.invoke(result);

            return switch (resultName) {
                case "PENDING" ->
                        NegotiationConversationSnapshot.pending(
                                rawStatus
                        );
                case "ACCEPTED" ->
                        NegotiationConversationSnapshot.accepted(
                                rawStatus
                        );
                case "REJECTED" ->
                        NegotiationConversationSnapshot.rejected(
                                rawStatus
                        );
                case "CANCELLED" ->
                        NegotiationConversationSnapshot.cancelled(
                                rawStatus
                        );
                case "SELLER_COUNTER_OFFER" ->
                        NegotiationConversationSnapshot
                                .sellerCounterOffer(
                                        sellerPrice
                                );
                case "UNKNOWN" ->
                        NegotiationConversationSnapshot.unknown(
                                rawStatus
                        );
                default -> throw new IllegalStateException(
                        "Unsupported private-core conversation result: "
                                + resultName
                );
            };
        } catch (InvocationTargetException exception) {
            throw propagate(
                    "Private conversation inspection failed",
                    exception.getCause()
            );
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "Private Playwright core API is incompatible with this public runtime. "
                            + "Run .\\bootstrap-private-core.ps1 again after pulling both repositories.",
                    exception
            );
        }
    }

    public static ConversationActivitySnapshot inspectActivity(
            Page page
    ) {
        try {
            Class<?> inspectorType =
                    requireClass(ACTIVITY_INSPECTOR);
            Object inspector =
                    inspectorType.getDeclaredConstructor().newInstance();

            Method inspect = inspectorType.getMethod(
                    "inspect",
                    Page.class
            );

            Object result = inspect.invoke(
                    inspector,
                    page
            );

            Class<?> resultType = result.getClass();

            boolean inspectionSucceeded =
                    (boolean) resultType
                            .getMethod("inspectionSucceeded")
                            .invoke(result);
            boolean latestOwnOfferFound =
                    (boolean) resultType
                            .getMethod("latestOwnOfferFound")
                            .invoke(result);
            boolean sellerMessageAfterLatestOwnOffer =
                    (boolean) resultType
                            .getMethod(
                                    "sellerMessageAfterLatestOwnOffer"
                            )
                            .invoke(result);
            String latestSellerMessageText =
                    (String) resultType
                            .getMethod("latestSellerMessageText")
                            .invoke(result);
            LocalDateTime latestSellerMessageAt =
                    (LocalDateTime) resultType
                            .getMethod("latestSellerMessageAt")
                            .invoke(result);
            boolean readIndicatorAfterLatestOwnOffer =
                    (boolean) resultType
                            .getMethod(
                                    "readIndicatorAfterLatestOwnOffer"
                            )
                            .invoke(result);

            return new ConversationActivitySnapshot(
                    inspectionSucceeded,
                    latestOwnOfferFound,
                    sellerMessageAfterLatestOwnOffer,
                    latestSellerMessageText,
                    latestSellerMessageAt,
                    readIndicatorAfterLatestOwnOffer
            );
        } catch (InvocationTargetException exception) {
            throw propagate(
                    "Private conversation-activity inspection failed",
                    exception.getCause()
            );
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "Private Playwright core API is incompatible with this public runtime. "
                            + "Run .\\bootstrap-private-core.ps1 again after pulling both repositories.",
                    exception
            );
        }
    }

    private static Class<?> requireClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException(
                    "Private FlipBot Playwright core is missing. "
                            + "From the playwright directory run: "
                            + ".\\bootstrap-private-core.ps1 "
                            + "and then reload the Maven project.",
                    exception
            );
        }
    }

    private static IllegalStateException propagate(
            String message,
            Throwable cause
    ) {
        if (cause instanceof RuntimeException runtimeException) {
            return new IllegalStateException(
                    message + ": " + runtimeException.getMessage(),
                    runtimeException
            );
        }

        return new IllegalStateException(
                message,
                cause
        );
    }
}
