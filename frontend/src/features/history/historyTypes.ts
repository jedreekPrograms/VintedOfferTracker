import type {
    ListingHistoryStatus,
} from "../../api/historyApi";

export type HistoryFilter =
    | "ALL"
    | ListingHistoryStatus;

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
    botId: string;
    searchQuery: string;
    sort: HistorySort;
}
