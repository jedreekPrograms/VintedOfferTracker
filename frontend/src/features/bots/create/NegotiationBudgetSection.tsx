interface NegotiationBudgetSectionProps {
    dailyNegotiationBudget: string;
    onBudgetChange: (value: string) => void;
}

function NegotiationBudgetSection({
    dailyNegotiationBudget,
    onBudgetChange,
}: NegotiationBudgetSectionProps) {
    return (
        <article className="content-card">
            <div className="bot-form-section-header">
                <div>
                    <span className="bot-form-step">
                        4
                    </span>

                    <h2 className="content-card-title">
                        Budżet negocjacyjny
                    </h2>
                </div>

                <p className="content-card-text">
                    Maksymalna liczba realnych akcji cenowych bota w ciągu dnia.
                    Liczą się zarówno pierwsze oferty, jak i kolejne kroki.
                    Przyszłe kroki aktywnych rozmów nie są rezerwowane z góry.
                </p>
            </div>

            <div className="form-field bot-budget-field">
                <label
                    className="form-label"
                    htmlFor="negotiation-budget"
                >
                    Dzienny limit ofert i kroków
                </label>

                <input
                    id="negotiation-budget"
                    className="form-input"
                    type="number"
                    min="1"
                    max="25"
                    step="1"
                    value={dailyNegotiationBudget}
                    onChange={(event) => {
                        onBudgetChange(
                            event.target.value,
                        );
                    }}
                />

                <span className="form-help">
                    Maksymalna wartość to 25. Każde faktycznie wysłane działanie
                    cenowe zużywa jeden punkt dopiero w momencie wysłania.
                </span>
            </div>
        </article>
    );
}

export default NegotiationBudgetSection;
