import AppSelect, {
    type AppSelectOption,
} from "../../../components/AppSelect";

interface OfferStrategySectionProps {
    autoRaiseOfferToVintedMinimum: boolean;
    maxAutomaticOffer: string;
    firstConfiguredOffer: string;
    modeDisabled?: boolean;
    minimumNegotiationCap?: number | null;
    onAutoRaiseChange: (value: boolean) => void;
    onMaxAutomaticOfferChange: (value: string) => void;
}

const autoRaiseOptions: AppSelectOption[] = [
    {
        value: "NO",
        label: "Nie — używaj dokładnych kwot z kroków",
    },
    {
        value: "YES",
        label: "Tak — skaluj drabinkę automatycznie",
    },
];

function OfferStrategySection({
    autoRaiseOfferToVintedMinimum,
    maxAutomaticOffer,
    firstConfiguredOffer,
    modeDisabled = false,
    minimumNegotiationCap = null,
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
                        Strategia negocjacji
                    </h2>
                </div>

                <p className="content-card-text">
                    Kwoty wpisane w krokach są bazową drabinką. Gdy pierwsza
                    oferta jest za niska dla Vinted, bot może podnieść ją do
                    bezpiecznej wartości i proporcjonalnie przeskalować kolejne kroki.
                </p>
            </div>

            {modeDisabled && (
                <div className="information-box">
                    Przy aktywnych negocjacjach nie można zmienić sposobu liczenia
                    drabinki, ale globalny limit negocjacji pozostaje edytowalny.
                    {minimumNegotiationCap !== null && (
                        <>
                            {" "}Nie możesz ustawić go poniżej już wysłanej kwoty{" "}
                            <strong>{formatNumber(minimumNegotiationCap)} zł</strong>.
                        </>
                    )}
                </div>
            )}

            <div className="bot-form-grid bot-form-grid-two">
                <div className="form-field">
                    <label
                        className="form-label"
                        htmlFor="auto-raise-offer"
                    >
                        Adaptacyjna drabinka cenowa
                    </label>

                    <AppSelect
                        id="auto-raise-offer"
                        value={autoRaiseValue}
                        options={autoRaiseOptions}
                        ariaLabel="Adaptacyjna drabinka negocjacyjna"
                        disabled={modeDisabled}
                        onChange={value => onAutoRaiseChange(value === "YES")}
                    />

                    <span className="form-help">
                        Bot najpierw próbuje ceny z kroku 1. Jeśli jest za niska,
                        pierwszą podniesioną ofertę zaokrągla w górę do następnych
                        50 zł. Kolejne kroki zachowują procentowe różnice między
                        skonfigurowanymi kwotami i są zaokrąglane w górę do 10 zł.
                    </span>
                </div>

                <div className="form-field">
                    <label
                        className="form-label"
                        htmlFor="max-automatic-offer"
                    >
                        Maksymalna cena negocjacji
                    </label>

                    <input
                        id="max-automatic-offer"
                        className="form-input"
                        type="number"
                        min={minimumNegotiationCap ?? 0.01}
                        step="0.01"
                        disabled={!autoRaiseOfferToVintedMinimum}
                        value={maxAutomaticOffer}
                        placeholder="np. 1500"
                        onChange={event =>
                            onMaxAutomaticOfferChange(event.target.value)}
                    />

                    <span className="form-help">
                        Globalny twardy limit. Bot nie wyśle ani nie zaakceptuje
                        automatycznie kwoty wyższej niż ta wartość, niezależnie od kroku.
                    </span>
                </div>
            </div>

            <div className="information-box">
                Bazowa pierwsza oferta:{" "}
                <strong>
                    {formatConfiguredPrice(firstConfiguredOffer)}
                </strong>
                {autoRaiseOfferToVintedMinimum && (
                    <>
                        {" "}· Globalny limit negocjacji:{" "}
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

function formatNumber(value: number): string {
    return new Intl.NumberFormat("pl-PL", {
        maximumFractionDigits: 2,
    }).format(value);
}

export default OfferStrategySection;
