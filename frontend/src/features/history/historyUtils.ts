import type {
    ListingHistoryResponse,
} from "../../api/historyApi";

import type {
    HistoryBotOption,
    HistorySort,
    HistoryViewFilters,
} from "./historyTypes";

export function getHistoryBots(
    listings: ListingHistoryResponse[],
): HistoryBotOption[] {
    const botsById = new Map<number, string>();

    for (const listing of listings) {
        botsById.set(listing.botId, listing.botName);
    }

    return Array.from(botsById.entries())
        .map(([id, name]) => ({ id, name }))
        .sort((first, second) =>
            first.name.localeCompare(second.name, "pl"),
        );
}

export function getFilteredHistory(
    listings: ListingHistoryResponse[],
    filters: HistoryViewFilters,
): ListingHistoryResponse[] {
    const normalizedSearch = filters.searchQuery
        .trim()
        .toLowerCase();

    return listings
        .filter(listing => {
            if (
                filters.status !== "ALL"
                && listing.status !== filters.status
            ) {
                return false;
            }

            if (
                filters.botId !== "ALL"
                && listing.botId !== Number(filters.botId)
            ) {
                return false;
            }

            if (normalizedSearch.length === 0) {
                return true;
            }

            return listing.title.toLowerCase().includes(normalizedSearch)
                || listing.listingId.toLowerCase().includes(normalizedSearch)
                || listing.botName.toLowerCase().includes(normalizedSearch);
        })
        .sort((first, second) =>
            compareListings(first, second, filters.sort),
        );
}

export function compareListings(
    first: ListingHistoryResponse,
    second: ListingHistoryResponse,
    sort: HistorySort,
): number {
    switch (sort) {
        case "OLDEST":
            return getDecisionTimestamp(first.decisionAt)
                - getDecisionTimestamp(second.decisionAt);

        case "BIGGEST_DISCOUNT":
            return calculateDiscountPercentage(
                second.originalPrice,
                second.currentPrice,
            ) - calculateDiscountPercentage(
                first.originalPrice,
                first.currentPrice,
            );

        case "LOWEST_PRICE":
            return first.currentPrice - second.currentPrice;

        case "NEWEST":
        default:
            return getDecisionTimestamp(second.decisionAt)
                - getDecisionTimestamp(first.decisionAt);
    }
}

export function calculateSavings(
    originalPrice: number,
    currentPrice: number,
): number {
    return Math.max(0, originalPrice - currentPrice);
}

export function calculateDiscountPercentage(
    originalPrice: number,
    currentPrice: number,
): number {
    if (originalPrice <= 0) {
        return 0;
    }

    return Math.max(
        0,
        ((originalPrice - currentPrice) / originalPrice) * 100,
    );
}

export function formatHistoryPrice(value: number): string {
    return new Intl.NumberFormat("pl-PL", {
        style: "currency",
        currency: "PLN",
    }).format(value);
}

export function formatHistoryPercentage(value: number): string {
    return `${new Intl.NumberFormat("pl-PL", {
        minimumFractionDigits: 1,
        maximumFractionDigits: 1,
    }).format(value)}%`;
}

export function formatDecisionDate(value: string | null): string {
    if (value === null) {
        return "Brak danych";
    }

    const date = new Date(value);

    if (Number.isNaN(date.getTime())) {
        return "Brak danych";
    }

    return new Intl.DateTimeFormat("pl-PL", {
        day: "2-digit",
        month: "2-digit",
        year: "numeric",
        hour: "2-digit",
        minute: "2-digit",
    }).format(date);
}

export function getAbsoluteVintedUrl(url: string): string {
    if (url.startsWith("http://") || url.startsWith("https://")) {
        return url;
    }

    return url.startsWith("/")
        ? `https://www.vinted.pl${url}`
        : `https://www.vinted.pl/${url}`;
}

export function getHistoryFilterClassName(active: boolean): string {
    return active
        ? "history-filter history-filter-active"
        : "history-filter";
}

export function parseHistorySort(value: string): HistorySort {
    switch (value) {
        case "OLDEST":
        case "BIGGEST_DISCOUNT":
        case "LOWEST_PRICE":
            return value;
        case "NEWEST":
        default:
            return "NEWEST";
    }
}

function getDecisionTimestamp(value: string | null): number {
    if (value === null) {
        return 0;
    }

    const timestamp = new Date(value).getTime();
    return Number.isNaN(timestamp) ? 0 : timestamp;
}
