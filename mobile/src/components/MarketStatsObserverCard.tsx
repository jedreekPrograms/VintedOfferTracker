import "../styles/market-stats-observer.css";

function MarketStatsObserverCard() {
    return (
        <article className="content-card market-observer-card">
            <div className="market-observer-header">
                <div>
                    <p className="market-observer-eyebrow">
                        Statystyki rynku
                    </p>
                    <h2 className="content-card-title">
                        Observer statystyk
                    </h2>
                    <p className="content-card-text">
                        Anonimowy, tylko do odczytu collector publicznego katalogu Vinted.
                        Nie wymaga konta, e-maila ani hasła i nigdy nie negocjuje.
                    </p>
                </div>
            </div>

            <div className="market-observer-summary market-observer-summary-system">
                <div className="market-observer-identity">
                    <strong>Anonymous Market Observer</strong>
                    <span>Bez konta Vinted i bez zapisanej sesji użytkownika</span>
                </div>

                <div className="market-observer-runtime">
                    <span className="market-observer-status-dot" />
                    <div>
                        <strong>Automatyczny collector</strong>
                        <span>
                            Uruchamia się razem z Playwrightem i skanuje wyłącznie publiczny katalog.
                        </span>
                    </div>
                </div>

                <span className="market-observer-system-badge">
                    READ ONLY
                </span>
            </div>
        </article>
    );
}

export default MarketStatsObserverCard;
