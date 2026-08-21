import type {
    CounterOfferRuleField,
    NegotiationStepField,
    NegotiationStepForm,
    NegotiationStepPolicyField,
} from "./botForm";

interface NegotiationStepsSectionProps {
    negotiationSteps: NegotiationStepForm[];
    dailyNegotiationBudget: string;
    definitionDisabled?: boolean;
    policyDisabled?: boolean;
    onAddStep: () => void;
    onRemoveStep: (stepId: number) => void;
    onUpdateStep: (stepId: number, field: NegotiationStepField, value: string) => void;
    onUpdateStepPolicy: (stepId: number, field: NegotiationStepPolicyField, value: string) => void;
    onAddCounterOfferRule: (stepId: number) => void;
    onRemoveCounterOfferRule: (stepId: number, ruleId: number) => void;
    onUpdateCounterOfferRule: (stepId: number, ruleId: number, field: CounterOfferRuleField, value: string) => void;
}

function NegotiationStepsSection({
    negotiationSteps,
    dailyNegotiationBudget,
    definitionDisabled = false,
    policyDisabled = false,
    onAddStep,
    onRemoveStep,
    onUpdateStep,
    onUpdateStepPolicy,
    onAddCounterOfferRule,
    onRemoveCounterOfferRule,
    onUpdateCounterOfferRule,
}: NegotiationStepsSectionProps) {
    return (
        <article className="content-card">
            <div className="bot-form-section-header">
                <div><span className="bot-form-step">6</span><h2 className="content-card-title">Kroki negocjacji</h2></div>
                <p className="content-card-text">
                    Cena i wiadomość definiują krok. Pod każdym krokiem możesz osobno ustawić,
                    kiedy bot ma przejść dalej po odrzuceniu, odczytaniu, braku odczytania
                    albo po kontrofecie sprzedającego.
                </p>
            </div>

            {definitionDisabled && (
                <div className="information-box">
                    Aktywne rozmowy blokują zmianę cen kroków, progów akceptacji,
                    wiadomości i liczby kroków, ponieważ zmieniłoby to znaczenie
                    aktualnego kroku. <strong>Reguły reakcji i czasy oczekiwania pozostają edytowalne</strong>.
                </div>
            )}

            <div className="negotiation-steps-list">
                {negotiationSteps.map((step, index) => {
                    const stepNumber = index + 1;
                    const isLastStep = index === negotiationSteps.length - 1;
                    return (
                        <article key={step.id} className="negotiation-step-card">
                            <div className="negotiation-step-header">
                                <div>
                                    <span className="negotiation-step-number">Krok {stepNumber}</span>
                                    <p className="negotiation-step-description">Oferta numer {stepNumber} w tej negocjacji.</p>
                                </div>
                                <button className="danger-text-button" type="button"
                                    disabled={definitionDisabled || negotiationSteps.length === 1}
                                    onClick={() => onRemoveStep(step.id)}>Usuń krok</button>
                            </div>

                            <div className="bot-form-grid bot-form-grid-two">
                                <div className="form-field">
                                    <label className="form-label" htmlFor={`offer-price-${step.id}`}>Cena oferty</label>
                                    <input id={`offer-price-${step.id}`} className="form-input" type="number" min="0.01" step="0.01"
                                        required disabled={definitionDisabled} value={step.offerPrice} placeholder="np. 900"
                                        onChange={(event) => onUpdateStep(step.id, "offerPrice", event.target.value)} />
                                    <span className="form-help">Bazowa kwota oferty. Przy adaptacyjnej drabince może zostać proporcjonalnie przeskalowana.</span>
                                </div>
                                <div className="form-field">
                                    <label className="form-label" htmlFor={`counter-offer-${step.id}`}>Akceptuj kontrofertę do</label>
                                    <input id={`counter-offer-${step.id}`} className="form-input" type="number" min="0.01" step="0.01"
                                        required disabled={definitionDisabled} value={step.maxAcceptedCounterOffer} placeholder="np. 950"
                                        onChange={(event) => onUpdateStep(step.id, "maxAcceptedCounterOffer", event.target.value)} />
                                    <span className="form-help">Jeśli sprzedający poda cenę nie wyższą niż ten próg, oferta trafia od razu do „Oferty do kupienia”.</span>
                                </div>
                            </div>

                            <div className="form-field negotiation-message-field">
                                <label className="form-label" htmlFor={`message-${step.id}`}>Wiadomość</label>
                                <textarea id={`message-${step.id}`} className="form-textarea" value={step.message} maxLength={1000} rows={3}
                                    required disabled={definitionDisabled} placeholder="np. Dzień dobry, czy zaakceptuje Pan/Pani taką cenę?"
                                    onChange={(event) => onUpdateStep(step.id, "message", event.target.value)} />
                                <div className="textarea-counter">{step.message.length}/1000</div>
                            </div>

                            <section className="negotiation-response-policy">
                                <div className="negotiation-response-policy-heading">
                                    <div><p className="negotiation-response-policy-eyebrow">Reakcja po kroku {stepNumber}</p><h3>Co bot robi po odpowiedzi lub braku reakcji?</h3></div>
                                    <span className="negotiation-policy-badge">Reguły przyszłych akcji</span>
                                </div>

                                {isLastStep && (
                                    <div className="negotiation-policy-note">To obecnie ostatni krok. Po upływie timerów, jeśli nie ma kolejnego kroku, rozmowa zostanie zakończona zamiast wysyłać nieistniejącą ofertę.</div>
                                )}

                                <div className="negotiation-policy-grid">
                                    <div className="negotiation-policy-card">
                                        <div className="negotiation-policy-card-title">
                                            <span className="negotiation-policy-icon">×</span>
                                            <div><strong>Jeśli sprzedający odrzuci ofertę</strong><p>Formalne „Odrzucono”, bez własnej propozycji ceny.</p></div>
                                        </div>
                                        <ReactionEditor idPrefix={`rejection-${step.id}`} action={step.rejectionAction} waitHours={step.rejectionWaitHours}
                                            disabled={policyDisabled}
                                            onActionChange={(value) => onUpdateStepPolicy(step.id, "rejectionAction", value)}
                                            onWaitHoursChange={(value) => onUpdateStepPolicy(step.id, "rejectionWaitHours", value)} />
                                    </div>

                                    <div className="negotiation-policy-card">
                                        <div className="negotiation-policy-card-title">
                                            <span className="negotiation-policy-icon">◷</span>
                                            <div><strong>Odczytanie i brak odczytania</strong><p>Osobne timery liczone dla aktualnego kroku. Po ich upływie bot przechodzi do następnego kroku, jeśli taki istnieje.</p></div>
                                        </div>
                                        <div className="bot-form-grid bot-form-grid-two">
                                            <div className="form-field">
                                                <label className="form-label" htmlFor={`read-wait-${step.id}`}>Po odczytaniu czekaj</label>
                                                <div className="input-with-suffix">
                                                    <input id={`read-wait-${step.id}`} className="form-input" type="number" min="1" max="720" step="1" required
                                                        disabled={policyDisabled} value={step.readWaitHours}
                                                        onChange={(event) => onUpdateStepPolicy(step.id, "readWaitHours", event.target.value)} />
                                                    <span>h</span>
                                                </div>
                                            </div>
                                            <div className="form-field">
                                                <label className="form-label" htmlFor={`unread-wait-${step.id}`}>Bez odczytania czekaj</label>
                                                <div className="input-with-suffix">
                                                    <input id={`unread-wait-${step.id}`} className="form-input" type="number" min="1" max="720" step="1" required
                                                        disabled={policyDisabled} value={step.unreadWaitHours}
                                                        onChange={(event) => onUpdateStepPolicy(step.id, "unreadWaitHours", event.target.value)} />
                                                    <span>h</span>
                                                </div>
                                            </div>
                                        </div>
                                    </div>

                                    <div className="negotiation-policy-card negotiation-policy-card-wide">
                                        <div className="negotiation-policy-card-title">
                                            <span className="negotiation-policy-icon">%</span>
                                            <div><strong>Jeśli sprzedający poda własną cenę</strong><p>Procent zawsze liczymy od <strong>początkowej ceny ogłoszenia</strong>.</p></div>
                                        </div>
                                        <div className="counter-rule-fallback">
                                            <div><strong>Gdy żaden próg poniżej nie pasuje</strong><span>Np. obniżka mniejsza niż 10%, jeśli pierwszy próg ustawisz na 10%.</span></div>
                                            <ReactionEditor idPrefix={`counter-default-${step.id}`} action={step.counterOfferDefaultAction}
                                                waitHours={step.counterOfferDefaultWaitHours} disabled={policyDisabled} compact
                                                onActionChange={(value) => onUpdateStepPolicy(step.id, "counterOfferDefaultAction", value)}
                                                onWaitHoursChange={(value) => onUpdateStepPolicy(step.id, "counterOfferDefaultWaitHours", value)} />
                                        </div>

                                        <div className="counter-rules-list">
                                            {step.counterOfferRules.slice().sort((left, right) => {
                                                const leftValue = Number(left.minimumDiscountPercent);
                                                const rightValue = Number(right.minimumDiscountPercent);
                                                if (!Number.isFinite(leftValue)) return 1;
                                                if (!Number.isFinite(rightValue)) return -1;
                                                return leftValue - rightValue;
                                            }).map((rule) => (
                                                <div key={rule.id} className="counter-rule-row">
                                                    <div className="counter-rule-threshold">
                                                        <label className="form-label" htmlFor={`discount-threshold-${step.id}-${rule.id}`}>Od obniżki</label>
                                                        <div className="input-with-suffix">
                                                            <input id={`discount-threshold-${step.id}-${rule.id}`} className="form-input" type="number" min="0.001" max="100" step="any"
                                                                disabled={policyDisabled} value={rule.minimumDiscountPercent} placeholder="10"
                                                                onChange={(event) => onUpdateCounterOfferRule(step.id, rule.id, "minimumDiscountPercent", event.target.value)} />
                                                            <span>%</span>
                                                        </div>
                                                    </div>
                                                    <ReactionEditor idPrefix={`counter-rule-${step.id}-${rule.id}`} action={rule.action} waitHours={rule.waitHours}
                                                        disabled={policyDisabled} compact
                                                        onActionChange={(value) => onUpdateCounterOfferRule(step.id, rule.id, "action", value)}
                                                        onWaitHoursChange={(value) => onUpdateCounterOfferRule(step.id, rule.id, "waitHours", value)} />
                                                    <button type="button" className="danger-text-button counter-rule-remove" disabled={policyDisabled}
                                                        onClick={() => onRemoveCounterOfferRule(step.id, rule.id)}>Usuń</button>
                                                </div>
                                            ))}
                                        </div>

                                        <button type="button" className="secondary-button counter-rule-add"
                                            disabled={policyDisabled || step.counterOfferRules.length >= 25}
                                            onClick={() => onAddCounterOfferRule(step.id)}>+ Dodaj kolejny próg procentowy</button>
                                        <p className="negotiation-policy-footnote">Gdy kilka progów pasuje, wygrywa <strong>najwyższy spełniony próg</strong>.</p>
                                    </div>
                                </div>
                            </section>
                        </article>
                    );
                })}
            </div>

            <button className="secondary-button add-negotiation-step-button" type="button"
                disabled={definitionDisabled || negotiationSteps.length >= 25} onClick={onAddStep}>+ Dodaj kolejny krok</button>

            <div className="negotiation-summary">
                <div><span>Liczba kroków</span><strong>{negotiationSteps.length}</strong></div>
                <div><span>Dzienny budżet</span><strong>{dailyNegotiationBudget || "—"}</strong></div>
                <div><span>Maks. nowych pełnych negocjacji</span><strong>{calculateMaximumFullNegotiations(dailyNegotiationBudget, negotiationSteps.length)}</strong></div>
            </div>
        </article>
    );
}

interface ReactionEditorProps {
    idPrefix: string;
    action: "NEXT_STEP_NOW" | "WAIT_BEFORE_NEXT_STEP";
    waitHours: string;
    disabled: boolean;
    compact?: boolean;
    onActionChange: (value: string) => void;
    onWaitHoursChange: (value: string) => void;
}

function ReactionEditor({ idPrefix, action, waitHours, disabled, compact = false, onActionChange, onWaitHoursChange }: ReactionEditorProps) {
    return (
        <div className={compact ? "reaction-editor reaction-editor-compact" : "reaction-editor"}>
            <div className="form-field">
                <label className="form-label" htmlFor={`${idPrefix}-action`}>Co robimy?</label>
                <select id={`${idPrefix}-action`} className="form-select" disabled={disabled} value={action}
                    onChange={(event) => onActionChange(event.target.value)}>
                    <option value="NEXT_STEP_NOW">Wyślij następny krok od razu</option>
                    <option value="WAIT_BEFORE_NEXT_STEP">Poczekaj, potem wyślij następny krok</option>
                </select>
            </div>
            {action === "WAIT_BEFORE_NEXT_STEP" && (
                <div className="form-field reaction-wait-field">
                    <label className="form-label" htmlFor={`${idPrefix}-hours`}>Ile godzin czekamy?</label>
                    <div className="input-with-suffix">
                        <input id={`${idPrefix}-hours`} className="form-input" type="number" min="1" max="720" step="1" required
                            disabled={disabled} value={waitHours} placeholder="np. 6"
                            onChange={(event) => onWaitHoursChange(event.target.value)} />
                        <span>h</span>
                    </div>
                </div>
            )}
        </div>
    );
}

function calculateMaximumFullNegotiations(budgetValue: string, numberOfSteps: number): string {
    const budget = Number(budgetValue);
    if (!Number.isInteger(budget) || budget <= 0 || numberOfSteps <= 0) return "—";
    return String(Math.floor(budget / numberOfSteps));
}

export default NegotiationStepsSection;
