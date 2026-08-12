import type {
    NegotiationStepField,
    NegotiationStepForm,
} from "./botForm";

interface NegotiationStepsSectionProps {
    negotiationSteps: NegotiationStepForm[];
    dailyNegotiationBudget: string;

    onAddStep: () => void;

    onRemoveStep: (
        stepId: number,
    ) => void;

    onUpdateStep: (
        stepId: number,
        field: NegotiationStepField,
        value: string,
    ) => void;
}

function NegotiationStepsSection({
    negotiationSteps,
    dailyNegotiationBudget,
    onAddStep,
    onRemoveStep,
    onUpdateStep,
}: NegotiationStepsSectionProps) {
    return (
        <article className="content-card">
            <div className="bot-form-section-header">
                <div>
                    <span className="bot-form-step">
                        6
                    </span>

                    <h2 className="content-card-title">
                        Kroki negocjacji
                    </h2>
                </div>

                <p className="content-card-text">
                    Kolejność elementów określa
                    kolejność kroków negocjacji
                </p>
            </div>

            <div className="negotiation-steps-list">
                {negotiationSteps.map(
                    (step, index) => {
                        const stepNumber =
                            index + 1;

                        return (
                            <article
                                key={step.id}
                                className="negotiation-step-card"
                            >
                                <div className="negotiation-step-header">
                                    <div>
                                        <span className="negotiation-step-number">
                                            Krok {stepNumber}
                                        </span>

                                        <p className="negotiation-step-description">
                                            Oferta numer{" "}
                                            {stepNumber} w tej
                                            negocjacji.
                                        </p>
                                    </div>

                                    <button
                                        className="danger-text-button"
                                        type="button"
                                        disabled={
                                            negotiationSteps.length
                                            === 1
                                        }
                                        onClick={() => {
                                            onRemoveStep(
                                                step.id,
                                            );
                                        }}
                                    >
                                        Usuń krok
                                    </button>
                                </div>

                                <div className="bot-form-grid bot-form-grid-two">
                                    <div className="form-field">
                                        <label
                                            className="form-label"
                                            htmlFor={`offer-price-${step.id}`}
                                        >
                                            Cena oferty
                                        </label>

                                        <input
                                            id={`offer-price-${step.id}`}
                                            className="form-input"
                                            type="number"
                                            min="0.01"
                                            step="0.01"
                                            required
                                            value={
                                                step.offerPrice
                                            }
                                            placeholder="np. 1500"
                                            onChange={(event) => {
                                                onUpdateStep(
                                                    step.id,
                                                    "offerPrice",
                                                    event.target.value,
                                                );
                                            }}
                                        />

                                        <span className="form-help">
                                            Kwota, którą bot
                                            zaproponuje
                                            sprzedającemu.
                                        </span>
                                    </div>

                                    <div className="form-field">
                                        <label
                                            className="form-label"
                                            htmlFor={`counter-offer-${step.id}`}
                                        >
                                            Akceptuj kontrofertę do
                                        </label>

                                        <input
                                            id={`counter-offer-${step.id}`}
                                            className="form-input"
                                            type="number"
                                            min="0.01"
                                            step="0.01"
                                            required
                                            value={
                                                step.maxAcceptedCounterOffer
                                            }
                                            placeholder="np. 1550"
                                            onChange={(event) => {
                                                onUpdateStep(
                                                    step.id,
                                                    "maxAcceptedCounterOffer",
                                                    event.target.value,
                                                );
                                            }}
                                        />

                                        <span className="form-help">
                                            Pole wymagane. Jeżeli 
                                            sprzedający zaproponuje 
                                            maksymalnie tę kwotę, 
                                            oferta trafi do
                                            „Oferty do kupienia”.
                                        </span>
                                    </div>
                                </div>

                                <div className="form-field negotiation-message-field">
                                    <label
                                        className="form-label"
                                        htmlFor={`message-${step.id}`}
                                    >
                                        Wiadomość
                                    </label>

                                    <textarea
                                        id={`message-${step.id}`}
                                        className="form-textarea"
                                        value={step.message}
                                        maxLength={1000}
                                        rows={3}
                                        required
                                        placeholder="np. Dzień dobry, czy zaakceptuje Pan/Pani taką cenę?"
                                        onChange={(event) => {
                                            onUpdateStep(
                                                step.id,
                                                "message",
                                                event.target.value,
                                            );
                                        }}
                                    />

                                    <div className="textarea-counter">
                                        {step.message.length}
                                        /1000
                                    </div>
                                </div>
                            </article>
                        );
                    },
                )}
            </div>

            <button
                className="secondary-button add-negotiation-step-button"
                type="button"
                disabled={
                    negotiationSteps.length
                    >= 25
                }
                onClick={onAddStep}
            >
                + Dodaj kolejny krok
            </button>

            <div className="negotiation-summary">
                <div>
                    <span>
                        Liczba kroków
                    </span>

                    <strong>
                        {negotiationSteps.length}
                    </strong>
                </div>

                <div>
                    <span>
                        Dzienny budżet
                    </span>

                    <strong>
                        {dailyNegotiationBudget || "—"}
                    </strong>
                </div>

                <div>
                    <span>
                        Maks. nowych pełnych negocjacji
                    </span>

                    <strong>
                        {calculateMaximumFullNegotiations(
                            dailyNegotiationBudget,
                            negotiationSteps.length,
                        )}
                    </strong>
                </div>
            </div>
        </article>
    );
}

function calculateMaximumFullNegotiations(
    budgetValue: string,
    numberOfSteps: number,
): string {
    const budget =
        Number(
            budgetValue,
        );

    if (
        !Number.isInteger(budget)
        || budget <= 0
        || numberOfSteps <= 0
    ) {
        return "—";
    }

    return String(
        Math.floor(
            budget / numberOfSteps,
        ),
    );
}

export default NegotiationStepsSection;