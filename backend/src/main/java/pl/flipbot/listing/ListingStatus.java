package pl.flipbot.listing;

public enum ListingStatus {

    DISCOVERED,

    NEGOTIATING,

    ACTION_REQUIRED,

    SKIPPED_OFFER_TOO_LOW,

    SKIPPED_OUTSIDE_PRICE_RANGE,

    SKIPPED_CANNOT_NEGOTIATE,

    SKIPPED_TARGET_MISMATCH,

    /*
     * Another FlipBot worker/account has already claimed this exact
     * marketplace listing for negotiation. The per-bot listing row is kept as
     * a terminal tombstone so this bot does not repeatedly retry it.
     */
    SKIPPED_ALREADY_NEGOTIATED,

    SKIPPED_BY_USER,

    UNAVAILABLE,

    CONTACT_UNAVAILABLE,

    REJECTED,

    EXPIRED,

    PURCHASED,

    FINISHED
}
