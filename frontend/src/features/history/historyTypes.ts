import type {
    HistoryOutcome,
    OfferAssessment,
} from "../../api/historyApi";

export type HistoryFilter =
    | "ALL"
    | HistoryOutcome;

export type HistoryAssessmentFilter =
    | "ALL"
    | OfferAssessment;

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
    outcome: HistoryFilter;
    assessment: HistoryAssessmentFilter;
    botId: string;
    searchQuery: string;
    sort: HistorySort;
}
