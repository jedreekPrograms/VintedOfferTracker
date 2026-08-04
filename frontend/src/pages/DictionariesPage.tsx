interface DictionarySection {
    title: string;
    description: string;
    example: string;
}

const dictionarySections: DictionarySection[] = [
    {
        title: "Kategorie",
        description:
            "Ręcznie dodawane kategorie i podkategorie dostępne podczas tworzenia bota.",
        example:
            "Elektronika → Telefony komórkowe → Smartfony",
    },
    {
        title: "Marki",
        description:
            "Lista marek przypisanych do wybranych kategorii.",
        example:
            "Samsung, Apple, Xiaomi",
    },
    {
        title: "Modele",
        description:
            "Pełna lista modeli dodawana ręcznie, niezależnie od ograniczonej listy wyświetlanej przez Vinted.",
        example:
            "Galaxy S25, Galaxy S25+, Galaxy S25 Ultra",
    },
];

function DictionariesPage() {
    return (
        <section className="page">
            <header className="page-header">
                <div>
                    <p className="page-eyebrow">
                        Dane konfiguracyjne
                    </p>

                    <h1 className="page-title">
                        Słowniki
                    </h1>

                    <p className="page-description">
                        Ręcznie zarządzaj kategoriami, markami i modelami
                        używanymi przy tworzeniu botów.
                    </p>
                </div>
            </header>

            <div className="dictionary-grid">
                {dictionarySections.map((section) => (
                    <article
                        key={section.title}
                        className="content-card"
                    >
                        <h2 className="content-card-title">
                            {section.title}
                        </h2>

                        <p className="content-card-text">
                            {section.description}
                        </p>

                        <div className="dictionary-example">
                            <span className="dictionary-example-label">
                                Przykład
                            </span>

                            <span>
                                {section.example}
                            </span>
                        </div>

                        <button
                            type="button"
                            className="secondary-button"
                            disabled
                        >
                            Zarządzaj
                        </button>
                    </article>
                ))}
            </div>
        </section>
    );
}

export default DictionariesPage;