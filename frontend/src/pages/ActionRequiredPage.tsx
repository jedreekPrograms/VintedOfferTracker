function ActionRequiredPage() {
    return (
        <section className="page">
            <header className="page-header">
                <div>
                    <p className="page-eyebrow">
                        Decyzja użytkownika
                    </p>

                    <h1 className="page-title">
                        Oferty do kupienia
                    </h1>

                    <p className="page-description">
                        Tutaj pojawią się oferty, dla których bot uzyskał
                        akceptowalną cenę.
                    </p>
                </div>
            </header>

            <article className="empty-state">
                <div className="status-badge status-badge-action">
                    ACTION_REQUIRED
                </div>

                <h2 className="empty-state-title">
                    Brak ofert oczekujących na zakup
                </h2>

                <p className="empty-state-description">
                    Bot nie kupuje automatycznie. Po uzyskaniu dobrej ceny
                    oferta pojawi się tutaj wraz z przyciskiem otwierającym
                    rozmowę na właściwym koncie Vinted.
                </p>
            </article>
        </section>
    );
}

export default ActionRequiredPage;