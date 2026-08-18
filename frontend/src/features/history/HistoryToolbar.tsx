import AppSelect, {
    type AppSelectOption,
} from "../../components/AppSelect";

import type {
    HistoryBotOption,
    HistorySort,
} from "./historyTypes";
import {
    parseHistorySort,
} from "./historyUtils";

const sortOptions: AppSelectOption[] = [
    { value: "NEWEST", label: "Najnowsze" },
    { value: "OLDEST", label: "Najstarsze" },
    { value: "BIGGEST_DISCOUNT", label: "Największy rabat" },
    { value: "LOWEST_PRICE", label: "Najniższa cena" },
];

interface HistoryToolbarProps {
    bots: HistoryBotOption[];
    selectedBotId: string;
    searchQuery: string;
    sort: HistorySort;
    filtersActive: boolean;
    onBotChange: (botId: string) => void;
    onSearchChange: (value: string) => void;
    onSortChange: (sort: HistorySort) => void;
    onClear: () => void;
}

function HistoryToolbar({
    bots,
    selectedBotId,
    searchQuery,
    sort,
    filtersActive,
    onBotChange,
    onSearchChange,
    onSortChange,
    onClear,
}: HistoryToolbarProps) {
    const botOptions: AppSelectOption[] = [
        { value: "ALL", label: "Wszystkie boty" },
        ...bots.map(bot => ({
            value: String(bot.id),
            label: bot.name,
        })),
    ];

    return (
        <div className="history-toolbar">
            <div className="history-search">
                <label htmlFor="history-search">Szukaj</label>
                <input
                    id="history-search"
                    type="search"
                    value={searchQuery}
                    placeholder="Nazwa, Listing ID lub bot..."
                    onChange={event => onSearchChange(event.target.value)}
                />
            </div>

            <div className="history-toolbar-field">
                <label htmlFor="history-bot-filter">Bot</label>
                <AppSelect
                    id="history-bot-filter"
                    value={selectedBotId}
                    options={botOptions}
                    ariaLabel="Filtr bota w historii"
                    onChange={onBotChange}
                />
            </div>

            <div className="history-toolbar-field">
                <label htmlFor="history-sort">Sortowanie</label>
                <AppSelect
                    id="history-sort"
                    value={sort}
                    options={sortOptions}
                    ariaLabel="Sortowanie historii"
                    onChange={value => onSortChange(parseHistorySort(value))}
                />
            </div>

            {filtersActive && (
                <button
                    className="secondary-button history-clear-button"
                    type="button"
                    onClick={onClear}
                >
                    Wyczyść filtry
                </button>
            )}
        </div>
    );
}

export default HistoryToolbar;
