interface OfferStrategySectionProps {
    autoRaiseOfferToVintedMinimum: boolean;

    maxAutomaticOffer: string;

    firstConfiguredOffer: string;

    onAutoRaiseChange: (
        value: boolean,
    ) => void;

    onMaxAutomaticOfferChange: (
        value: string,
    ) => void;
}

function OfferStrategySection({
    autoRaiseOfferToVintedMinimum,
    maxAutomaticOffer,
    firstConfiguredOffer,
    onAutoRaiseChange,
    onMaxAutomaticOfferChange,
}: OfferStrategySectionProps) {
    return (
        <article className="content-card">
            <div className="bot-form-section-header">
                <div>
                    <span className="bot-form-step">
                        5
                    </span>

                    <h2 className="content-card-title">
                        Strategia pierwszej oferty
                    </h2>
                </div>

                <p className="content-card-text">
                    Określa, co zrobić, gdy
                    pierwsza skonfigurowana oferta
                    jest niższa niż minimum
                    dopuszczane przez Vinted.
                </p>
            </div>

            <div className="bot-form-grid bot-form-grid-two">
                <div className="form-field">
                    <label
                        className="form-label"
                        htmlFor="auto-raise-offer"
                    >
                        Automatyczne podniesienie
                    </label>

                    <select
                        id="auto-raise-offer"
                        className="form-select"
                        value={
                            autoRaiseOfferToVintedMinimum
                                ? "YES"
                                : "NO"
                        }
                        onChange={(event) => {
                            onAutoRaiseChange(
                                event.target.value
                                === "YES",
                            );
                        }}
                    >
                        <option value="NO">
                            Nie — pomiń ofertę
                        </option>

                        <option value="YES">
                            Tak — podnieś do minimum Vinted
                        </option>
                    </select>

                    <span className="form-help">
                        Pierwsza oferta nadal zaczyna się
                        od ceny z kroku 1. Bot podniesie ją
                        tylko wtedy, gdy Vinted wymaga więcej.
                    </span>
                </div>

                <div className="form-field">
                    <label
                        className="form-label"
                        htmlFor="max-automatic-offer"
                    >
                        Maksymalna automatyczna oferta
                    </label>

                    <input
                        id="max-automatic-offer"
                        className="form-input"
                        type="number"
                        min="0.01"
                        step="0.01"
                        disabled={
                            !autoRaiseOfferToVintedMinimum
                        }
                        value={
                            maxAutomaticOffer
                        }
                        placeholder="np. 1250"
                        onChange={(event) => {
                            onMaxAutomaticOfferChange(
                                event.target.value,
                            );
                        }}
                    />

                    <span className="form-help">
                        Twardy limit bezpieczeństwa.
                        Bot nie podniesie automatycznej
                        pierwszej oferty powyżej tej kwoty.
                    </span>
                </div>
            </div>

            <div className="information-box">
                Pierwszy krok negocjacji:{" "}
                <strong>
                    {firstConfiguredOffer.trim().length > 0
                        ? `${firstConfiguredOffer} zł`
                        : "nie ustawiono"}
                </strong>
                {autoRaiseOfferToVintedMinimum && (
                    <>
                        {" "}· Maksimum automatyczne:{" "}
                        <strong>
                            {maxAutomaticOffer.trim().length > 0
                                ? `${maxAutomaticOffer} zł`
                                : "nie ustawiono"}
                        </strong>
                    </>
                )}
            </div>
        </article>
    );
}

export default OfferStrategySection;
