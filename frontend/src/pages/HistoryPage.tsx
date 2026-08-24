import {
    useCallback,
    useEffect,
    useMemo,
    useState,
} from "react";

import {
    getListingHistory,
    type ListingHistoryResponse,
} from "../api/historyApi";
import HistoryCard from "../features/history/HistoryCard";
import HistoryStatusFilters from "../features/history/HistoryStatusFilters";
import HistoryToolbar from "../features/history/HistoryToolbar";
import type {
    HistoryFilter,
    HistorySort,
} from "../features/history/historyTypes";
import {
    getFilteredHistory,
    getHistoryBots,
} from "../features/history/historyUtils";

const DEFAULT_FILTER: HistoryFilter = "ALL";
const DEFAULT_BOT_ID = "ALL";
const DEFAULT_SORT: HistorySort = "NEWEST";

function HistoryPage() {
    const [listings, setListings] = useState<ListingHistoryResponse[]>([]);
    const [filter, setFilter] = useState<HistoryFilter>(DEFAULT_FILTER);
    const [selectedBotId, setSelectedBotId] = useState(DEFAULT_BOT_ID);
    const [searchQuery, setSearchQuery] = useState("");
    const [sort, setSort] = useState<HistorySort>(DEFAULT_SORT);
    const [isLoading, setIsLoading] = useState(true);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);

    const loadHistory = useCallback(async () => {
        setIsLoading(true);
        setErrorMessage(null);

        try {
            setListings(await getListingHistory());
        } catch (error) {
            setErrorMessage(
                error instanceof Error
                    ? error.message
                    : "Nie udało się pobrać historii.",
            );
        } finally {
            setIsLoading(false);
        }
    }, []);

    useEffect(() => {
        void loadHistory();
    }, [loadHistory]);

    const purchasedCount = useMemo(
        () => listings.filter(listing => listing.status === "PURCHASED").length,
        [listings],
    );
    const skippedCount = useMemo(
        () => listings.filter(listing => listing.status === "SKIPPED_BY_USER").length,
        [listings],
    );
    const bots = useMemo(
        () => getHistoryBots(listings),
        [listings],
    );
    const filteredListings = useMemo(
        () => getFilteredHistory(listings, {
            status: filter,
            botId: selectedBotId,
            searchQuery,
            sort,
        }),
        [listings, filter, selectedBotId, searchQuery, sort],
    );

    const filtersActive = filter !== DEFAULT_FILTER
        || selectedBotId !== DEFAULT_BOT_ID
        || searchQuery.trim().length > 0
        || sort !== DEFAULT_SORT;

    function clearFilters() {
        setFilter(DEFAULT_FILTER);
        setSelectedBotId(DEFAULT_BOT_ID);
        setSearchQuery("");
        setSort(DEFAULT_SORT);
    }

    function updateListing(updatedListing: ListingHistoryResponse) {
        setListings(currentListings => currentListings.map(listing => (
            listing.id === updatedListing.id
                ? updatedListing
                : listing
        )));
    }

    function removeListing(listingId: number) {
        setListings(currentListings => currentListings.filter(
            listing => listing.id !== listingId,
        ));
    }

    return (
        <section className="page">
            <header className="page-header">
                <div>
                    <p className="page-eyebrow">Archiwum decyzji</p>
                    <h1 className="page-title">Historia</h1>
                    <p className="page-description">
                        Kupione i ręcznie odrzucone oferty po zakończeniu negocjacji.
                    </p>
                </div>

                <button
                    className="secondary-button"
                    type="button"
                    disabled={isLoading}
                    onClick={() => void loadHistory()}
                >
                    {isLoading ? "Odświeżanie..." : "Odśwież"}
                </button>
            </header>

            {errorMessage !== null && (
                <div className="form-message form-message-error" role="alert">
                    {errorMessage}
                </div>
            )}

            <HistoryStatusFilters
                value={filter}
                totalCount={listings.length}
                purchasedCount={purchasedCount}
                skippedCount={skippedCount}
                onChange={setFilter}
            />

            <HistoryToolbar
                bots={bots}
                selectedBotId={selectedBotId}
                searchQuery={searchQuery}
                sort={sort}
                filtersActive={filtersActive}
                onBotChange={setSelectedBotId}
                onSearchChange={setSearchQuery}
                onSortChange={setSort}
                onClear={clearFilters}
            />

            <div className="history-results-header">
                <span>Wyniki</span>
                <strong>{filteredListings.length}</strong>
            </div>

            {isLoading && listings.length === 0 ? (
                <article className="content-card">
                    <div className="dictionary-list-state">
                        Pobieranie historii...
                    </div>
                </article>
            ) : filteredListings.length === 0 ? (
                <HistoryEmptyState
                    filtersActive={filtersActive}
                    onClear={clearFilters}
                />
            ) : (
                <div className="history-list">
                    {filteredListings.map(listing => (
                        <HistoryCard
                            key={listing.id}
                            listing={listing}
                            onUpdated={updateListing}
                            onRemoved={removeListing}
                        />
                    ))}
                </div>
            )}
        </section>
    );
}

interface HistoryEmptyStateProps {
    filtersActive: boolean;
    onClear: () => void;
}

function HistoryEmptyState({
    filtersActive,
    onClear,
}: HistoryEmptyStateProps) {
    return (
        <article className="content-card">
            <div className="history-empty">
                <div className="history-empty-icon">⌕</div>
                <h2>Brak pasujących ofert</h2>
                <p>Zmień filtry albo wyszukiwaną frazę.</p>

                {filtersActive && (
                    <button
                        className="secondary-button"
                        type="button"
                        onClick={onClear}
                    >
                        Wyczyść filtry
                    </button>
                )}
            </div>
        </article>
    );
}

export default HistoryPage;
