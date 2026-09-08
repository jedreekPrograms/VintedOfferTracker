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
                    Dzienna pula realnych akcji cenowych. Każda rozpoczęta
                    rozmowa rezerwuje miejsce na wszystkie jeszcze niewysłane
                    kroki. Gdy rozmowa kończy się wcześniej, niewykorzystana
                    rezerwa wraca do puli i może pozwolić uruchomić kolejną
                    pełną negocjację.
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
                    Maksymalna wartość to 25. O północy licznik faktycznie
                    wysłanych akcji zaczyna nowy dzień, ale aktywne rozmowy nadal
                    rezerwują swoje pozostałe kroki. Np. trzy rozmowy na kroku 3
                    przy drabince 5-krokowej rezerwują na nowy dzień 6 akcji.
                </span>
            </div>
        </article>
    );
}

export default NegotiationBudgetSection;
