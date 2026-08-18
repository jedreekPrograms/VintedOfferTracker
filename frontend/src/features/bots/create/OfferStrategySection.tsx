import AppSelect, {
    type AppSelectOption,
} from "../../../components/AppSelect";

interface OfferStrategySectionProps {
    autoRaiseOfferToVintedMinimum: boolean;
    maxAutomaticOffer: string;
    firstConfiguredOffer: string;
    onAutoRaiseChange: (value: boolean) => void;
    onMaxAutomaticOfferChange: (value: string) => void;
}

const autoRaiseOptions: AppSelectOption[] = [
    {
        value: "NO",
        label: "Nie — pomiń ofertę",
    },
    {
        value: "YES",
        label: "Tak — podnieś do minimum Vinted",
    },
];

function OfferStrategySection({
    autoRaiseOfferToVintedMinimum,
    maxAutomaticOffer,
    firstConfiguredOffer,
    onAutoRaiseChange,
    onMaxAutomaticOfferChange,
}: OfferStrategySectionProps) {
    const autoRaiseValue = autoRaiseOfferToVintedMinimum
        ? "YES"
        : "NO";

    return (
        <article className="content-card">
            <div className="bot-form-section-header">
                <div>
                    <span className="bot-form-step">5</span>
                    <h2 className="content-card-title">
                        Strategia pierwszej oferty
                    </h2>
                </div>

                <p className="content-card-text">
                    Określa, co zrobić, gdy pierwsza skonfigurowana oferta
                    jest niższa niż minimum dopuszczane przez Vinted.
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

                    <AppSelect
                        id="auto-raise-offer"
                        value={autoRaiseValue}
                        options={autoRaiseOptions}
                        ariaLabel="Automatyczne podniesienie pierwszej oferty"
                        onChange={value => onAutoRaiseChange(value === "YES")}
                    />

                    <span className="form-help">
                        Pierwsza oferta nadal zaczyna się od ceny z kroku 1.
                        Bot podniesie ją tylko wtedy, gdy Vinted wymaga więcej.
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
                        disabled={!autoRaiseOfferToVintedMinimum}
                        value={maxAutomaticOffer}
                        placeholder="np. 1250"
                        onChange={event =>
                            onMaxAutomaticOfferChange(event.target.value)}
                    />

                    <span className="form-help">
                        Twardy limit bezpieczeństwa. Bot nie podniesie
                        automatycznej pierwszej oferty powyżej tej kwoty.
                    </span>
                </div>
            </div>

            <div className="information-box">
                Pierwszy krok negocjacji:{" "}
                <strong>
                    {formatConfiguredPrice(firstConfiguredOffer)}
                </strong>
                {autoRaiseOfferToVintedMinimum && (
                    <>
                        {" "}· Maksimum automatyczne:{" "}
                        <strong>
                            {formatConfiguredPrice(maxAutomaticOffer)}
                        </strong>
                    </>
                )}
            </div>
        </article>
    );
}

function formatConfiguredPrice(value: string): string {
    return value.trim().length > 0
        ? `${value} zł`
        : "nie ustawiono";
}

export default OfferStrategySection;
