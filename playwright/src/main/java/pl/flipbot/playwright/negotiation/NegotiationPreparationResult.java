package pl.flipbot.playwright.negotiation;

public enum NegotiationPreparationResult {

    PREPARED,

    OFFER_TOO_LOW,

    LISTING_UNAVAILABLE,

    /*
     * The listing passed catalog/URL prefiltering but its live item-page title
     * no longer matches the configured bot target. No quota was reserved.
     */
    TARGET_MISMATCH,

    /*
     * Listing jest dostępny i poprawnie załadowany, ale Vinted nie pokazuje
     * temu kontu akcji pozwalającej rozpocząć negocjację ceny.
     *
     * To nie jest błąd workera i nie wymaga quota.
     */
    CANNOT_NEGOTIATE

}
