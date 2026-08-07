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
                    Określa maksymalną liczbę
                    zarezerwowanych kroków negocjacji.
                </p>
            </div>

            <div className="form-field bot-budget-field">
                <label
                    className="form-label"
                    htmlFor="negotiation-budget"
                >
                    Dzienny budżet
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
                    Maksymalna wartość to 25.
                </span>
            </div>
        </article>
    );
}

export default NegotiationBudgetSection;