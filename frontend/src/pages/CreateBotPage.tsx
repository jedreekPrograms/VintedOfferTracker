function CreateBotPage() {
    return (
        <section className="page">
            <header className="page-header">
                <div>
                    <p className="page-eyebrow">
                        Konfiguracja
                    </p>

                    <h1 className="page-title">
                        Utwórz bota
                    </h1>

                    <p className="page-description">
                        Skonfiguruj konto Vinted, filtry wyszukiwania
                        i kolejne kroki negocjacji.
                    </p>
                </div>
            </header>

            <article className="content-card">
                <h2 className="content-card-title">
                    Formularz utworzenia bota
                </h2>

                <p className="content-card-text">
                    W następnym etapie dodamy tutaj pola konta Vinted,
                    kategorii, marki, modelu, przedziału cenowego i kroków
                    negocjacyjnych.
                </p>

                <div className="information-box">
                    Jedno konto Vinted może być przypisane wyłącznie
                    do jednego bota.
                </div>
            </article>
        </section>
    );
}

export default CreateBotPage;