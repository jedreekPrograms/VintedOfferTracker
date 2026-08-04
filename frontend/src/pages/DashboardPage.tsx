interface DashboardStat {
    label: string;
    value: number;
    description: string;
}

const dashboardStats: DashboardStat[] = [
    {
        label: "Aktywne boty",
        value: 0,
        description: "Boty ze statusem RUNNING",
    },
    {
        label: "Trwające negocjacje",
        value: 0,
        description: "Oferty obsługiwane przez boty",
    },
    {
        label: "Oferty do kupienia",
        value: 0,
        description: "Oferty ze statusem ACTION_REQUIRED",
    },
    {
        label: "Zakończone negocjacje",
        value: 0,
        description: "Kupione lub ręcznie zamknięte",
    },
];

function DashboardPage() {
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
                        Podsumowanie działania botów i negocjowanych ofert.
                    </p>
                </div>
            </header>

            <div className="stats-grid">
                {dashboardStats.map((stat) => (
                    <article
                        key={stat.label}
                        className="stat-card"
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
                ))}
            </div>

            <article className="content-card">
                <h2 className="content-card-title">
                    Jak działa panel
                </h2>

                <p className="content-card-text">
                    Każdy bot korzysta z osobnego konta Vinted i posiada
                    własne filtry, limit propozycji oraz konfigurację
                    negocjacji.
                </p>

                <p className="content-card-text">
                    Gdy bot osiągnie akceptowalną cenę, ogłoszenie otrzyma
                    status ACTION_REQUIRED i pojawi się w zakładce
                    „Oferty do kupienia”.
                </p>
            </article>
        </section>
    );
}

export default DashboardPage;