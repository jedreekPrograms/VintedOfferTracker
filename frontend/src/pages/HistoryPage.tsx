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


type HistoryFilter =
    | "ALL"
    | "PURCHASED"
    | "SKIPPED_BY_USER";


type HistorySort =
    | "NEWEST"
    | "OLDEST"
    | "BIGGEST_DISCOUNT"
    | "LOWEST_PRICE";


function HistoryPage() {

    const [
        listings,
        setListings,
    ] = useState<ListingHistoryResponse[]>(
        [],
    );


    const [
        filter,
        setFilter,
    ] = useState<HistoryFilter>(
        "ALL",
    );


    const [
        selectedBotId,
        setSelectedBotId,
    ] = useState<string>(
        "ALL",
    );


    const [
        searchQuery,
        setSearchQuery,
    ] = useState(
        "",
    );


    const [
        sort,
        setSort,
    ] = useState<HistorySort>(
        "NEWEST",
    );


    const [
        isLoading,
        setIsLoading,
    ] = useState(
        true,
    );


    const [
        errorMessage,
        setErrorMessage,
    ] = useState<string | null>(
        null,
    );


    const loadHistory =
        useCallback(
            async () => {

                setIsLoading(
                    true,
                );

                setErrorMessage(
                    null,
                );

                try {

                    const history =
                        await getListingHistory();

                    setListings(
                        history,
                    );

                } catch (error) {

                    setErrorMessage(
                        error instanceof Error
                            ? error.message
                            : "Nie udało się pobrać historii.",
                    );

                } finally {

                    setIsLoading(
                        false,
                    );
                }
            },
            [],
        );


    useEffect(
        () => {

            void loadHistory();

        },
        [
            loadHistory,
        ],
    );


    const purchasedCount =
        useMemo(
            () =>
                listings.filter(
                    listing =>
                        listing.status
                        === "PURCHASED",
                ).length,
            [
                listings,
            ],
        );


    const skippedCount =
        useMemo(
            () =>
                listings.filter(
                    listing =>
                        listing.status
                        === "SKIPPED_BY_USER",
                ).length,
            [
                listings,
            ],
        );


    const bots =
        useMemo(
            () => {

                const botsById =
                    new Map<
                        number,
                        string
                    >();

                listings.forEach(
                    listing => {

                        botsById.set(
                            listing.botId,
                            listing.botName,
                        );
                    },
                );

                return Array.from(
                    botsById.entries(),
                )
                    .map(
                        ([
                            id,
                            name,
                        ]) => ({
                            id,
                            name,
                        }),
                    )
                    .sort(
                        (first, second) =>
                            first.name.localeCompare(
                                second.name,
                                "pl",
                            ),
                    );
            },
            [
                listings,
            ],
        );


    const filteredListings =
        useMemo(
            () => {

                const normalizedSearch =
                    searchQuery
                        .trim()
                        .toLowerCase();


                const result =
                    listings.filter(
                        listing => {

                            if (
                                filter !== "ALL"
                                && listing.status
                                !== filter
                            ) {

                                return false;
                            }


                            if (
                                selectedBotId !== "ALL"
                                && listing.botId
                                !== Number(
                                    selectedBotId,
                                )
                            ) {

                                return false;
                            }


                            if (
                                normalizedSearch.length
                                > 0
                            ) {

                                const titleMatches =
                                    listing.title
                                        .toLowerCase()
                                        .includes(
                                            normalizedSearch,
                                        );


                                const listingIdMatches =
                                    listing.listingId
                                        .toLowerCase()
                                        .includes(
                                            normalizedSearch,
                                        );


                                const botMatches =
                                    listing.botName
                                        .toLowerCase()
                                        .includes(
                                            normalizedSearch,
                                        );


                                if (
                                    !titleMatches
                                    && !listingIdMatches
                                    && !botMatches
                                ) {

                                    return false;
                                }
                            }


                            return true;
                        },
                    );


                return [
                    ...result,
                ].sort(
                    (first, second) =>
                        compareListings(
                            first,
                            second,
                            sort,
                        ),
                );
            },
            [
                listings,
                filter,
                selectedBotId,
                searchQuery,
                sort,
            ],
        );


    const filtersActive =
        filter !== "ALL"
        || selectedBotId !== "ALL"
        || searchQuery.trim().length > 0
        || sort !== "NEWEST";


    function clearFilters() {

        setFilter(
            "ALL",
        );

        setSelectedBotId(
            "ALL",
        );

        setSearchQuery(
            "",
        );

        setSort(
            "NEWEST",
        );
    }


    return (

        <section className="page">


            <header className="page-header">

                <div>

                    <p className="page-eyebrow">
                        Archiwum decyzji
                    </p>

                    <h1 className="page-title">
                        Historia
                    </h1>

                    <p className="page-description">
                        Kupione i ręcznie odrzucone
                        oferty po zakończeniu negocjacji.
                    </p>

                </div>


                <button
                    className="secondary-button"
                    type="button"
                    disabled={isLoading}
                    onClick={() => {
                        void loadHistory();
                    }}
                >

                    {isLoading
                        ? "Odświeżanie..."
                        : "Odśwież"}

                </button>

            </header>


            {errorMessage !== null && (

                <div
                    className="
                        form-message
                        form-message-error
                    "
                    role="alert"
                >
                    {errorMessage}
                </div>

            )}


            <div className="history-filters">

                <button
                    className={
                        getFilterClassName(
                            filter === "ALL",
                        )
                    }
                    type="button"
                    onClick={() => {
                        setFilter(
                            "ALL",
                        );
                    }}
                >

                    Wszystkie

                    <span>
                        {listings.length}
                    </span>

                </button>


                <button
                    className={
                        getFilterClassName(
                            filter === "PURCHASED",
                        )
                    }
                    type="button"
                    onClick={() => {
                        setFilter(
                            "PURCHASED",
                        );
                    }}
                >

                    Kupione

                    <span>
                        {purchasedCount}
                    </span>

                </button>


                <button
                    className={
                        getFilterClassName(
                            filter
                            === "SKIPPED_BY_USER",
                        )
                    }
                    type="button"
                    onClick={() => {
                        setFilter(
                            "SKIPPED_BY_USER",
                        );
                    }}
                >

                    Odrzucone

                    <span>
                        {skippedCount}
                    </span>

                </button>

            </div>


            <div className="history-toolbar">


                <div className="history-search">

                    <label
                        htmlFor="history-search"
                    >
                        Szukaj
                    </label>

                    <input
                        id="history-search"
                        type="text"
                        value={searchQuery}
                        placeholder="Nazwa, Listing ID lub bot..."
                        onChange={
                            event => {

                                setSearchQuery(
                                    event.target.value,
                                );
                            }
                        }
                    />

                </div>


                <div className="history-toolbar-field">

                    <label
                        htmlFor="history-bot-filter"
                    >
                        Bot
                    </label>

                    <select
                        id="history-bot-filter"
                        value={selectedBotId}
                        onChange={
                            event => {

                                setSelectedBotId(
                                    event.target.value,
                                );
                            }
                        }
                    >

                        <option value="ALL">
                            Wszystkie boty
                        </option>

                        {bots.map(
                            bot => (

                                <option
                                    key={bot.id}
                                    value={bot.id}
                                >
                                    {bot.name}
                                </option>

                            ),
                        )}

                    </select>

                </div>


                <div className="history-toolbar-field">

                    <label
                        htmlFor="history-sort"
                    >
                        Sortowanie
                    </label>

                    <select
                        id="history-sort"
                        value={sort}
                        onChange={
                            event => {

                                setSort(
                                    parseHistorySort(
                                        event.target.value,
                                    ),
                                );
                            }
                        }
                    >

                        <option value="NEWEST">
                            Najnowsze
                        </option>

                        <option value="OLDEST">
                            Najstarsze
                        </option>

                        <option value="BIGGEST_DISCOUNT">
                            Największy rabat
                        </option>

                        <option value="LOWEST_PRICE">
                            Najniższa cena
                        </option>

                    </select>

                </div>


                {filtersActive && (

                    <button
                        className="secondary-button history-clear-button"
                        type="button"
                        onClick={
                            clearFilters
                        }
                    >
                        Wyczyść filtry
                    </button>

                )}

            </div>


            <div className="history-results-header">

                <span>
                    Wyniki
                </span>

                <strong>
                    {filteredListings.length}
                </strong>

            </div>


            {isLoading && listings.length === 0 ? (

                <article className="content-card">

                    <div className="dictionary-list-state">
                        Pobieranie historii...
                    </div>

                </article>

            ) : filteredListings.length === 0 ? (

                <article className="content-card">

                    <div className="history-empty">

                        <div className="history-empty-icon">
                            ⌕
                        </div>

                        <h2>
                            Brak pasujących ofert
                        </h2>

                        <p>
                            Zmień filtry albo
                            wyszukiwaną frazę.
                        </p>

                        {filtersActive && (

                            <button
                                className="secondary-button"
                                type="button"
                                onClick={
                                    clearFilters
                                }
                            >
                                Wyczyść filtry
                            </button>

                        )}

                    </div>

                </article>

            ) : (

                <div className="history-list">

                    {filteredListings.map(
                        listing => {

                            const savings =
                                calculateSavings(
                                    listing.originalPrice,
                                    listing.currentPrice,
                                );


                            const discount =
                                calculateDiscountPercentage(
                                    listing.originalPrice,
                                    listing.currentPrice,
                                );


                            const listingUrl =
                                getAbsoluteVintedUrl(
                                    listing.url,
                                );


                            const purchased =
                                listing.status
                                === "PURCHASED";


                            return (

                                <article
                                    key={listing.id}
                                    className="history-card"
                                >


                                    <div className="history-card-main">


                                        <div className="history-card-header">

                                            <div>

                                                <span
                                                    className={
                                                        purchased
                                                            ? "history-status history-status-purchased"
                                                            : "history-status history-status-skipped"
                                                    }
                                                >

                                                    {purchased
                                                        ? "✓ Kupione"
                                                        : "✕ Odrzucone"}

                                                </span>


                                                <h2>
                                                    {listing.title}
                                                </h2>

                                            </div>


                                            <div className="history-decision-date">

                                                <span>
                                                    Data decyzji
                                                </span>

                                                <strong>
                                                    {formatDecisionDate(
                                                        listing.decisionAt,
                                                    )}
                                                </strong>

                                            </div>

                                        </div>


                                        <div className="history-price-row">

                                            <div>

                                                <span>
                                                    Cena początkowa
                                                </span>

                                                <strong className="history-original-price">
                                                    {formatPrice(
                                                        listing.originalPrice,
                                                    )}
                                                </strong>

                                            </div>


                                            <div className="history-price-arrow">
                                                →
                                            </div>


                                            <div>

                                                <span>
                                                    Cena po negocjacji
                                                </span>

                                                <strong className="history-current-price">
                                                    {formatPrice(
                                                        listing.currentPrice,
                                                    )}
                                                </strong>

                                            </div>


                                            <div className="history-saving">

                                                <span>
                                                    Wynegocjowano
                                                </span>

                                                <strong>
                                                    {formatPrice(
                                                        savings,
                                                    )}
                                                </strong>

                                                <small>
                                                    -
                                                    {formatPercentage(
                                                        discount,
                                                    )}
                                                </small>

                                            </div>

                                        </div>


                                        <div className="history-details">

                                            <div>

                                                <span>
                                                    Bot
                                                </span>

                                                <strong>
                                                    {listing.botName}
                                                </strong>

                                                <small>
                                                    #{listing.botId}
                                                </small>

                                            </div>


                                            <div>

                                                <span>
                                                    Listing ID
                                                </span>

                                                <strong>
                                                    {listing.listingId}
                                                </strong>

                                            </div>


                                            <div>

                                                <span>
                                                    Krok negocjacji
                                                </span>

                                                <strong>
                                                    {listing.currentStep}
                                                </strong>

                                            </div>


                                            <div>

                                                <span>
                                                    Status
                                                </span>

                                                <strong>
                                                    {listing.status}
                                                </strong>

                                            </div>

                                        </div>

                                    </div>


                                    <div className="history-card-actions">

                                        <a
                                            className="secondary-button"
                                            href={listingUrl}
                                            target="_blank"
                                            rel="noreferrer"
                                        >
                                            Otwórz ofertę
                                        </a>

                                    </div>

                                </article>
                            );
                        },
                    )}

                </div>
            )}

        </section>
    );
}


function compareListings(
    first: ListingHistoryResponse,
    second: ListingHistoryResponse,
    sort: HistorySort,
): number {

    switch (sort) {

        case "OLDEST":

            return getDecisionTimestamp(
                first.decisionAt,
            )
                - getDecisionTimestamp(
                    second.decisionAt,
                );


        case "BIGGEST_DISCOUNT":

            return calculateDiscountPercentage(
                second.originalPrice,
                second.currentPrice,
            )
                - calculateDiscountPercentage(
                    first.originalPrice,
                    first.currentPrice,
                );


        case "LOWEST_PRICE":

            return first.currentPrice
                - second.currentPrice;


        case "NEWEST":
        default:

            return getDecisionTimestamp(
                second.decisionAt,
            )
                - getDecisionTimestamp(
                    first.decisionAt,
                );
    }
}


function getDecisionTimestamp(
    value: string | null,
): number {

    if (
        value === null
    ) {

        return 0;
    }


    const timestamp =
        new Date(
            value,
        ).getTime();


    return Number.isNaN(
        timestamp,
    )
        ? 0
        : timestamp;
}


function getFilterClassName(
    active: boolean,
): string {

    return active
        ? "history-filter history-filter-active"
        : "history-filter";
}


function calculateSavings(
    originalPrice: number,
    currentPrice: number,
): number {

    return Math.max(
        0,
        originalPrice - currentPrice,
    );
}


function calculateDiscountPercentage(
    originalPrice: number,
    currentPrice: number,
): number {

    if (
        originalPrice <= 0
    ) {

        return 0;
    }


    return Math.max(
        0,
        (
            (
                originalPrice
                - currentPrice
            )
            / originalPrice
        )
        * 100,
    );
}


function formatPrice(
    value: number,
): string {

    return new Intl.NumberFormat(
        "pl-PL",
        {
            style: "currency",
            currency: "PLN",
        },
    ).format(
        value,
    );
}


function formatPercentage(
    value: number,
): string {

    return new Intl.NumberFormat(
        "pl-PL",
        {
            minimumFractionDigits: 1,
            maximumFractionDigits: 1,
        },
    ).format(
        value,
    ) + "%";
}


function formatDecisionDate(
    value: string | null,
): string {

    if (
        value === null
    ) {

        return "Brak danych";
    }


    const date =
        new Date(
            value,
        );


    if (
        Number.isNaN(
            date.getTime(),
        )
    ) {

        return "Brak danych";
    }


    return new Intl.DateTimeFormat(
        "pl-PL",
        {
            day: "2-digit",
            month: "2-digit",
            year: "numeric",
            hour: "2-digit",
            minute: "2-digit",
        },
    ).format(
        date,
    );
}


function getAbsoluteVintedUrl(
    url: string,
): string {

    if (
        url.startsWith(
            "http://",
        )
        || url.startsWith(
            "https://",
        )
    ) {

        return url;
    }


    if (
        url.startsWith(
            "/",
        )
    ) {

        return `https://www.vinted.pl${url}`;
    }


    return `https://www.vinted.pl/${url}`;
}

function parseHistorySort(
    value: string,
): HistorySort {

    switch (value) {

        case "NEWEST":
            return "NEWEST";

        case "OLDEST":
            return "OLDEST";

        case "BIGGEST_DISCOUNT":
            return "BIGGEST_DISCOUNT";

        case "LOWEST_PRICE":
            return "LOWEST_PRICE";

        default:
            return "NEWEST";
    }
}


export default HistoryPage;