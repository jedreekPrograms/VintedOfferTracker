package pl.flipbot.playwright.negotiation;

public enum NegotiationPreparationResult {

    PREPARED,

    OFFER_TOO_LOW,

    LISTING_UNAVAILABLE,

    /*
     * Listing jest dostępny i poprawnie załadowany, ale Vinted nie pokazuje
     * temu kontu akcji pozwalającej rozpocząć negocjację ceny.
     *
     * To nie jest błąd workera i nie wymaga quota.
     */
    CANNOT_NEGOTIATE

}