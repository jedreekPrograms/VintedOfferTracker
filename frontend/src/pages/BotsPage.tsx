import { Link } from "react-router-dom";

function BotsPage() {
    return (
        <section className="page">
            <header className="page-header page-header-with-action">
                <div>
                    <p className="page-eyebrow">
                        Zarządzanie
                    </p>

                    <h1 className="page-title">
                        Boty
                    </h1>

                    <p className="page-description">
                        Każdy bot korzysta z jednego, osobnego konta Vinted.
                    </p>
                </div>

                <Link
                    to="/bots/create"
                    className="primary-button"
                >
                    Utwórz bota
                </Link>
            </header>

            <article className="empty-state">
                <h2 className="empty-state-title">
                    Brak botów
                </h2>

                <p className="empty-state-description">
                    Po połączeniu z backendem w tym miejscu pojawi się lista
                    utworzonych botów.
                </p>

                <Link
                    to="/bots/create"
                    className="secondary-button"
                >
                    Przejdź do formularza
                </Link>
            </article>
        </section>
    );
}

export default BotsPage;