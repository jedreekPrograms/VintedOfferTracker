import {
    useCallback,
    useEffect,
    useState,
} from "react";

import {
    getDashboardStats,
    type DashboardPeriod,
    type DashboardStatsResponse,
} from "../api/dashboardApi";


interface DashboardStat {

    label: string;

    value: string;

    description: string;

    variant?:
        | "default"
        | "success"
        | "warning";
}


interface PeriodOption {

    value: DashboardPeriod;

    label: string;
}


const periodOptions: PeriodOption[] = [

    {
        value: "TODAY",
        label: "Dzisiaj",
    },

    {
        value: "LAST_7_DAYS",
        label: "7 dni",
    },

    {
        value: "LAST_30_DAYS",
        label: "30 dni",
    },

    {
        value: "ALL",
        label: "Całość",
    },
];


function DashboardPage() {

    const [
        stats,
        setStats,
    ] = useState<DashboardStatsResponse | null>(
        null,
    );


    const [
        period,
        setPeriod,
    ] = useState<DashboardPeriod>(
        "ALL",
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


    const loadStats =
        useCallback(
            async () => {

                setIsLoading(
                    true,
                );

                setErrorMessage(
                    null,
                );


                try {

                    const response =
                        await getDashboardStats(
                            period,
                        );

                    setStats(
                        response,
                    );

                } catch (error) {

                    setErrorMessage(
                        error instanceof Error
                            ? error.message
                            : "Nie udało się pobrać statystyk.",
                    );

                } finally {

                    setIsLoading(
                        false,
                    );
                }
            },
            [
                period,
            ],
        );


    useEffect(
        () => {

            void loadStats();

        },
        [
            loadStats,
        ],
    );


    const activityStats: DashboardStat[] =
        stats === null
            ? []
            : [

                {
                    label:
                        "Aktywne boty",

                    value:
                        stats.activeBotsCount
                            .toString(),

                    description:
                        "Boty ze statusem RUNNING",
                },

                {
                    label:
                        "Trwające negocjacje",

                    value:
                        stats.negotiatingCount
                            .toString(),

                    description:
                        "Oferty ze statusem NEGOTIATING",
                },

                {
                    label:
                        "Oferty do kupienia",

                    value:
                        stats.actionRequiredCount
                            .toString(),

                    description:
                        "Czekają na Twoją decyzję",

                    variant:
                        stats.actionRequiredCount > 0
                            ? "warning"
                            : "default",
                },

            ];


    const periodStats: DashboardStat[] =
        stats === null
            ? []
            : [

                {
                    label:
                        "Kupione",

                    value:
                        stats.purchasedCount
                            .toString(),

                    description:
                        getPeriodDescription(
                            period,
                        ),

                    variant:
                        "success",
                },

                {
                    label:
                        "Odrzucone",

                    value:
                        stats.skippedByUserCount
                            .toString(),

                    description:
                        "Oferty odrzucone ręcznie",
                },

                {
                    label:
                        "Łącznie wydano",

                    value:
                        formatPrice(
                            stats.totalSpent,
                        ),

                    description:
                        "Suma cen zakupionych ofert",
                },

                {
                    label:
                        "Wynegocjowano",

                    value:
                        formatPrice(
                            stats.totalNegotiatedSavings,
                        ),

                    description:
                        "Różnica względem cen początkowych",

                    variant:
                        "success",
                },

                {
                    label:
                        "Średnia cena zakupu",

                    value:
                        formatPrice(
                            stats.averagePurchasePrice,
                        ),

                    description:
                        "Średnia cena kupionej oferty",
                },

                {
                    label:
                        "Średni rabat",

                    value:
                        formatPercentage(
                            stats.averageDiscountPercentage,
                        ),

                    description:
                        "Średnia obniżka ceny",

                    variant:
                        "success",
                },

            ];


    const purchaseRate =
        stats === null
            ? 0
            : calculatePurchaseRate(
                stats.purchasedCount,
                stats.skippedByUserCount,
            );


    return (

        <section className="page">


            <header className="page-header">

                <div>

                    <p className="page-eyebrow">
                        Panel główny
                    </p>

                    <h1 className="page-title">
                        Dashboard
                    </h1>

                    <p className="page-description">

                        Podsumowanie działania botów,
                        negocjacji i zakupionych ofert.

                    </p>

                </div>


                <button
                    className="secondary-button"
                    type="button"
                    disabled={isLoading}
                    onClick={() => {
                        void loadStats();
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


            <div className="dashboard-section-header">

                <div>

                    <h2>
                        Aktualny stan
                    </h2>

                    <p>
                        Dane niezależne od wybranego okresu.
                    </p>

                </div>

            </div>


            {stats !== null && (

                <div className="stats-grid">

                    {activityStats.map(
                        stat => (

                            <StatCard
                                key={stat.label}
                                stat={stat}
                            />

                        ),
                    )}

                </div>

            )}


            <div className="dashboard-period-section">


                <div className="dashboard-section-header">

                    <div>

                        <h2>
                            Wyniki
                        </h2>

                        <p>
                            Statystyki decyzji
                            i zakupów w wybranym okresie.
                        </p>

                    </div>

                </div>


                <div className="dashboard-period-switch">

                    {periodOptions.map(
                        option => {

                            const active =
                                period === option.value;


                            return (

                                <button
                                    key={option.value}
                                    className={
                                        active
                                            ? "dashboard-period-button dashboard-period-button-active"
                                            : "dashboard-period-button"
                                    }
                                    type="button"
                                    disabled={isLoading}
                                    onClick={() => {

                                        setPeriod(
                                            option.value,
                                        );

                                    }}
                                >

                                    {option.label}

                                </button>
                            );
                        },
                    )}

                </div>

            </div>


            {isLoading && stats === null ? (

                <article className="content-card">

                    <div className="dictionary-list-state">
                        Pobieranie statystyk...
                    </div>

                </article>

            ) : stats !== null ? (

                <>


                    <div className="stats-grid">

                        {periodStats.map(
                            stat => (

                                <StatCard
                                    key={stat.label}
                                    stat={stat}
                                />

                            ),
                        )}

                    </div>


                    <div className="dashboard-summary-grid">


                        <article className="content-card">

                            <h2 className="content-card-title">
                                Decyzje zakupowe
                            </h2>


                            <p className="content-card-text">

                                Oferty, które dotarły
                                do etapu decyzji użytkownika
                                w wybranym okresie.

                            </p>


                            <div className="dashboard-decision-stats">


                                <div>

                                    <span>
                                        Kupione
                                    </span>

                                    <strong>
                                        {stats.purchasedCount}
                                    </strong>

                                </div>


                                <div>

                                    <span>
                                        Odrzucone
                                    </span>

                                    <strong>
                                        {stats.skippedByUserCount}
                                    </strong>

                                </div>


                                <div>

                                    <span>
                                        Wszystkie decyzje
                                    </span>

                                    <strong>

                                        {
                                            stats.purchasedCount
                                            + stats.skippedByUserCount
                                        }

                                    </strong>

                                </div>


                            </div>

                        </article>


                        <article className="content-card">

                            <h2 className="content-card-title">
                                Skuteczność zakupu
                            </h2>


                            <p className="content-card-text">

                                Jaki procent podjętych
                                decyzji zakończył się zakupem.

                            </p>


                            <div className="dashboard-effectiveness">

                                <strong>
                                    {formatPercentage(
                                        purchaseRate,
                                    )}
                                </strong>

                                <span>
                                    ofert zakończonych zakupem
                                </span>

                            </div>

                        </article>


                    </div>


                    <article className="content-card">

                        <h2 className="content-card-title">
                            Wybrany okres
                        </h2>


                        <p className="content-card-text">

                            Aktualnie pokazujesz:

                            {" "}

                            <strong>
                                {getPeriodLabel(
                                    period,
                                )}
                            </strong>.

                        </p>


                        <p className="content-card-text">

                            Aktywne boty, trwające negocjacje
                            oraz oferty do kupienia zawsze
                            pokazują aktualny stan systemu.

                        </p>


                        <p className="content-card-text">

                            Kupione, odrzucone, wydana kwota,
                            wynegocjowana różnica i średnie
                            wartości są filtrowane według
                            daty decyzji.

                        </p>

                    </article>

                </>

            ) : null}


        </section>
    );
}


function StatCard({
    stat,
}: {
    stat: DashboardStat;
}) {

    return (

        <article
            className={
                [
                    "stat-card",

                    stat.variant !== undefined
                        ? `stat-card-${stat.variant}`
                        : "",

                ]
                    .filter(
                        Boolean,
                    )
                    .join(
                        " ",
                    )
            }
        >

            <div className="stat-label">
                {stat.label}
            </div>

            <div className="stat-value">
                {stat.value}
            </div>

            <div className="stat-description">
                {stat.description}
            </div>

        </article>
    );
}


function calculatePurchaseRate(
    purchasedCount: number,
    skippedCount: number,
): number {

    const total =
        purchasedCount
        + skippedCount;


    if (
        total === 0
    ) {

        return 0;
    }


    return (
        purchasedCount
        / total
    ) * 100;
}


function getPeriodDescription(
    period: DashboardPeriod,
): string {

    switch (period) {

        case "TODAY":
            return "Kupione dzisiaj";

        case "LAST_7_DAYS":
            return "Kupione w ostatnich 7 dniach";

        case "LAST_30_DAYS":
            return "Kupione w ostatnich 30 dniach";

        case "ALL":
            return "Kupione od początku";
    }
}


function getPeriodLabel(
    period: DashboardPeriod,
): string {

    switch (period) {

        case "TODAY":
            return "Dzisiaj";

        case "LAST_7_DAYS":
            return "Ostatnie 7 dni";

        case "LAST_30_DAYS":
            return "Ostatnie 30 dni";

        case "ALL":
            return "Cały okres";
    }
}


function formatPrice(
    value: number,
): string {

    return new Intl.NumberFormat(
        "pl-PL",
        {
            style: "currency",
            currency: "PLN",
            maximumFractionDigits: 2,
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


export default DashboardPage;