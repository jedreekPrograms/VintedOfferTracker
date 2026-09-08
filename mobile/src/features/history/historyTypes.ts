import type {
    ListingHistoryOutcome,
    ListingHistoryStatus,
} from "../../api/historyApi";

export type HistoryFilter =
    | "ALL"
    | ListingHistoryStatus;

export type HistoryOutcomeFilter =
    | "ALL"
    | "UNCLASSIFIED"
    | ListingHistoryOutcome;

export type HistorySort =
    | "NEWEST"
    | "OLDEST"
    | "BIGGEST_DISCOUNT"
    | "LOWEST_PRICE";

export interface HistoryBotOption {
    id: number;
    name: string;
}

export interface HistoryViewFilters {
    status: HistoryFilter;
    outcome: HistoryOutcomeFilter;
    botId: string;
    searchQuery: string;
    sort: HistorySort;
}
