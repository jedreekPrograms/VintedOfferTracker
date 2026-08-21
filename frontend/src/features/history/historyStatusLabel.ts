export function getListingStatusLabel(status: string): string {
    switch (status) {
        case "PURCHASED":
            return "Kupione";
        case "SKIPPED_BY_USER":
            return "Odrzucone przez użytkownika";
        case "SKIPPED_ALREADY_NEGOTIATED":
            return "Pominięto — negocjowane przez innego bota";
        case "SKIPPED_TARGET_MISMATCH":
            return "Pominięto — inny model / cel";
        case "SKIPPED_CANNOT_NEGOTIATE":
            return "Pominięto — brak możliwości negocjacji";
        case "SKIPPED_OFFER_TOO_LOW":
            return "Pominięto — oferta poniżej minimum Vinted";
        case "SKIPPED_OUTSIDE_PRICE_RANGE":
            return "Pominięto — poza zakresem ceny";
        case "UNAVAILABLE":
            return "Niedostępne";
        case "CONTACT_UNAVAILABLE":
            return "Kontakt niedostępny";
        case "EXPIRED":
            return "Wygasłe";
        case "REJECTED":
            return "Odrzucone";
        case "FINISHED":
            return "Zakończone";
        case "ACTION_REQUIRED":
            return "Wymaga decyzji";
        case "NEGOTIATING":
            return "Negocjowanie";
        case "DISCOVERED":
            return "Odkryte";
        default:
            return status.replaceAll("_", " ");
    }
}
