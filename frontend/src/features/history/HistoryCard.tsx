import { useState } from "react";

import {
    removeHistoryEntry,
    updateHistoryClassification,
    updateHistoryPurchasePrice,
    type HistoryOutcome,
    type ListingHistoryResponse,
    type MissedOpportunityReason,
    type OfferAssessment,
} from "../../api/historyApi";
import AppDialog from "../../components/AppDialog";
import AppSelect, {
    type AppSelectOption,
} from "../../components/AppSelect";
import { formatProductProvenance } from "../listings/productProvenance";
import "./HistoryCard.css";
import {
    calculateDiscountPercentage,
    calculateSavings,
    formatDecisionDate,
    formatHistoryPercentage,
    formatHistoryPrice,
    getAbsoluteVintedUrl,
} from "./historyUtils";

const outcomeOptions: AppSelectOption[] = [
    { value: "UNCLASSIFIED", label: "Do oznaczenia" },
    { value: "PURCHASED", label: "Kupiona" },
    { value: "REJECTED", label: "Odrzucona" },
    { value: "MISSED_OPPORTUNITY", label: "Utracona okazja" },
];

const assessmentOptions: AppSelectOption[] = [
    { value: "UNASSESSED", label: "Nieoceniona" },
    { value: "LEGIT", label: "Legit" },
    { value: "SCAM", label: "Oszustwo / podejrzana" },
];

const missedReasonOptions: AppSelectOption[] = [
    { value: "", label: "Bez powodu" },
    { value: "SOLD_BEFORE_PURCHASE", label: "Sprzedane przed zakupem" },
    { value: "TOO_SLOW", label: "Nie zdążyłem zareagować" },
    { value: "NO_FUNDS", label: "Brak środków" },
    { value: "OTHER", label: "Inne" },
];

interface HistoryCardProps {
    listing: ListingHistoryResponse;
    onUpdated: (listing: ListingHistoryResponse) => void;
    onRemoved: (listingId: number) => void;
}

function HistoryCard({
    listing,
    onUpdated,
    onRemoved,
}: HistoryCardProps) {
    const [editingPurchasePrice, setEditingPurchasePrice] = useState(false);
    const [purchasePriceDraft, setPurchasePriceDraft] = useState(String(listing.currentPrice));
    const [isSaving, setIsSaving] = useState(false);
    const [isClassifying, setIsClassifying] = useState(false);
    const [isRemoving, setIsRemoving] = useState(false);
    const [showRemoveConfirmation, setShowRemoveConfirmation] = useState(false);
    const [actionError, setActionError] = useState<string | null>(null);

    const savings = calculateSavings(listing.originalPrice, listing.currentPrice);
    const discount = calculateDiscountPercentage(listing.originalPrice, listing.currentPrice);
    const purchased = listing.historyOutcome === "PURCHASED";
    const productProvenance = formatProductProvenance(listing);

    async function saveClassification(
        historyOutcome: HistoryOutcome,
        offerAssessment: OfferAssessment,
        missedOpportunityReason: MissedOpportunityReason | null,
    ) {
        if (isClassifying) {
            return;
        }

        setIsClassifying(true);
        setActionError(null);

        try {
            const updated = await updateHistoryClassification(
                listing.id,
                historyOutcome,
                offerAssessment,
                historyOutcome === "MISSED_OPPORTUNITY"
                    ? missedOpportunityReason
                    : null,
            );
            onUpdated(updated);
        } catch (error) {
            setActionError(
                error instanceof Error
                    ? error.message
                    : "Nie udało się zapisać klasyfikacji.",
            );
        } finally {
            setIsClassifying(false);
        }
    }

    async function savePurchasePrice() {
        const purchasePrice = Number(purchasePriceDraft.replace(",", ".").trim());
        if (!Number.isFinite(purchasePrice) || purchasePrice <= 0) {
            setActionError("Podaj prawidłową cenę zakupu większą od 0 zł.");
            return;
        }

        setIsSaving(true);
        setActionError(null);
        try {
            const updatedListing = await updateHistoryPurchasePrice(listing.id, purchasePrice);
            onUpdated(updatedListing);
            setPurchasePriceDraft(String(updatedListing.currentPrice));
            setEditingPurchasePrice(false);
        } catch (error) {
            setActionError(
                error instanceof Error
                    ? error.message
                    : "Nie udało się zmienić ceny zakupu.",
            );
        } finally {
            setIsSaving(false);
        }
    }

    async function removeFromHistory() {
        if (isRemoving) {
            return;
        }

        setIsRemoving(true);
        setActionError(null);
        try {
            await removeHistoryEntry(listing.id);
            setShowRemoveConfirmation(false);
            onRemoved(listing.id);
        } catch (error) {
            setActionError(
                error instanceof Error
                    ? error.message
                    : "Nie udało się usunąć wpisu z historii.",
            );
        } finally {
            setIsRemoving(false);
        }
    }

    function cancelPurchasePriceEdit() {
        setPurchasePriceDraft(String(listing.currentPrice));
        setEditingPurchasePrice(false);
        setActionError(null);
    }

    return (
        <article className="history-card">
            <div className="history-card-main">
                <div className="history-card-header">
                    <div>
                        <span className={getOutcomeBadgeClass(listing.historyOutcome)}>
                            {getOutcomeLabel(listing.historyOutcome)}
                        </span>
                        <span className={getAssessmentBadgeClass(listing.offerAssessment)}>
                            {getAssessmentLabel(listing.offerAssessment)}
                        </span>
                        <h2>{listing.title}</h2>
                    </div>
                    <div className="history-decision-date">
                        <span>Data decyzji</span>
                        <strong>{formatDecisionDate(listing.decisionAt)}</strong>
                    </div>
                </div>

                <div className="history-classification-panel">
                    <div>
                        <label>Wynik</label>
                        <AppSelect
                            value={listing.historyOutcome}
                            options={outcomeOptions}
                            disabled={isClassifying}
                            ariaLabel="Wynik oferty"
                            onChange={value => void saveClassification(
                                value as HistoryOutcome,
                                listing.offerAssessment,
                                listing.missedOpportunityReason,
                            )}
                        />
                    </div>

                    <div>
                        <label>Ocena</label>
                        <AppSelect
                            value={listing.offerAssessment}
                            options={assessmentOptions}
                            disabled={isClassifying}
                            ariaLabel="Ocena oferty"
                            onChange={value => void saveClassification(
                                listing.historyOutcome,
                                value as OfferAssessment,
                                listing.missedOpportunityReason,
                            )}
                        />
                    </div>

                    {listing.historyOutcome === "MISSED_OPPORTUNITY" && (
                        <div>
                            <label>Powód utraty</label>
                            <AppSelect
                                value={listing.missedOpportunityReason ?? ""}
                                options={missedReasonOptions}
                                disabled={isClassifying}
                                ariaLabel="Powód utraty okazji"
                                onChange={value => void saveClassification(
                                    listing.historyOutcome,
                                    listing.offerAssessment,
                                    value.length === 0
                                        ? null
                                        : value as MissedOpportunityReason,
                                )}
                            />
                        </div>
                    )}

                    <div className="history-classification-state">
                        {isClassifying ? "Zapisywanie..." : "Zmiany zapisują się automatycznie"}
                    </div>
                </div>

                <div className="history-price-row">
                    <div>
                        <span>Cena początkowa</span>
                        <strong className="history-original-price">
                            {formatHistoryPrice(listing.originalPrice)}
                        </strong>
                    </div>
                    <div className="history-price-arrow">→</div>
                    <div>
                        <span>{purchased ? "Cena zakupu" : "Cena po negocjacji"}</span>
                        {purchased && editingPurchasePrice ? (
                            <form
                                className="history-price-editor"
                                onSubmit={(event) => {
                                    event.preventDefault();
                                    void savePurchasePrice();
                                }}
                            >
                                <div className="history-price-input-wrap">
                                    <input
                                        autoFocus
                                        aria-label="Cena zakupu"
                                        type="number"
                                        min="0.01"
                                        step="0.01"
                                        inputMode="decimal"
                                        value={purchasePriceDraft}
                                        disabled={isSaving}
                                        onChange={(event) => setPurchasePriceDraft(event.target.value)}
                                    />
                                    <span>zł</span>
                                </div>
                                <div className="history-price-editor-actions">
                                    <button
                                        className="primary-button history-compact-button"
                                        type="submit"
                                        disabled={isSaving}
                                    >
                                        {isSaving ? "Zapis..." : "Zapisz"}
                                    </button>
                                    <button
                                        className="secondary-button history-compact-button"
                                        type="button"
                                        disabled={isSaving}
                                        onClick={cancelPurchasePriceEdit}
                                    >
                                        Anuluj
                                    </button>
                                </div>
                            </form>
                        ) : (
                            <strong className="history-current-price">
                                {formatHistoryPrice(listing.currentPrice)}
                            </strong>
                        )}
                    </div>
                    <div className="history-saving">
                        <span>Wynegocjowano</span>
                        <strong>{formatHistoryPrice(savings)}</strong>
                        <small>-{formatHistoryPercentage(discount)}</small>
                    </div>
                </div>

                <div className="history-details">
                    <HistoryDetail label="Bot" value={listing.botName} secondary={`#${listing.botId}`} />
                    <HistoryDetail label="Produkt" value={productProvenance} />
                    <HistoryDetail label="Listing ID" value={listing.listingId} />
                    <HistoryDetail label="Krok negocjacji" value={String(listing.currentStep)} />
                    <HistoryDetail label="Status techniczny" value={listing.status} />
                </div>

                {actionError !== null && (
                    <div className="history-action-error" role="alert">
                        {actionError}
                    </div>
                )}
            </div>

            <div className="history-card-actions">
                <a
                    className="secondary-button"
                    href={getAbsoluteVintedUrl(listing.url)}
                    target="_blank"
                    rel="noreferrer"
                >
                    Otwórz ofertę
                </a>
                {purchased && !editingPurchasePrice && (
                    <button
                        className="secondary-button"
                        type="button"
                        disabled={isRemoving}
                        onClick={() => {
                            setPurchasePriceDraft(String(listing.currentPrice));
                            setActionError(null);
                            setEditingPurchasePrice(true);
                        }}
                    >
                        Zmień cenę
                    </button>
                )}
                <button
                    className="history-remove-button"
                    type="button"
                    disabled={isRemoving || isSaving || isClassifying}
                    onClick={() => setShowRemoveConfirmation(true)}
                >
                    {isRemoving ? "Usuwanie..." : "Usuń z historii"}
                </button>
            </div>

            <AppDialog
                open={showRemoveConfirmation}
                title="Usunąć wpis z historii?"
                description={
                    <>Oferta <strong>„{listing.title}”</strong> zniknie z historii widocznej w aplikacji. Techniczny zapis pozostanie w bazie, żeby bot nie potraktował jej ponownie jako nowej.</>
                }
                confirmLabel="Usuń z historii"
                danger
                busy={isRemoving}
                onCancel={() => setShowRemoveConfirmation(false)}
                onConfirm={() => void removeFromHistory()}
            />
        </article>
    );
}

function getOutcomeLabel(outcome: HistoryOutcome): string {
    switch (outcome) {
        case "PURCHASED":
            return "✓ Kupiona";
        case "REJECTED":
            return "✕ Odrzucona";
        case "MISSED_OPPORTUNITY":
            return "◷ Utracona okazja";
        case "UNCLASSIFIED":
        default:
            return "• Do oznaczenia";
    }
}

function getAssessmentLabel(assessment: OfferAssessment): string {
    switch (assessment) {
        case "LEGIT":
            return "Legit";
        case "SCAM":
            return "Oszustwo";
        case "UNASSESSED":
        default:
            return "Nieoceniona";
    }
}

function getOutcomeBadgeClass(outcome: HistoryOutcome): string {
    return `history-status history-status-${outcome.toLowerCase().replaceAll("_", "-")}`;
}

function getAssessmentBadgeClass(assessment: OfferAssessment): string {
    return `history-assessment-badge history-assessment-${assessment.toLowerCase()}`;
}

function HistoryDetail({
    label,
    value,
    secondary,
}: {
    label: string;
    value: string;
    secondary?: string;
}) {
    return (
        <div>
            <span>{label}</span>
            <strong>{value}</strong>
            {secondary !== undefined && <small>{secondary}</small>}
        </div>
    );
}

export default HistoryCard;
