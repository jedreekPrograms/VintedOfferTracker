import { useState } from "react";

import {
    removeHistoryEntry,
    updateHistoryOutcome,
    updateHistoryPurchasePrice,
    type ListingHistoryResponse,
} from "../../api/historyApi";
import AppDialog from "../../components/AppDialog";
import "./HistoryCard.css";
import {
    calculateDiscountPercentage,
    calculateSavings,
    formatDecisionDate,
    formatHistoryPercentage,
    formatHistoryPrice,
    getAbsoluteVintedUrl,
    getHistoryOutcomeLabel,
    HISTORY_OUTCOME_OPTIONS,
    parseHistoryOutcome,
} from "./historyUtils";

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
    const [isSavingOutcome, setIsSavingOutcome] = useState(false);
    const [isRemoving, setIsRemoving] = useState(false);
    const [showRemoveConfirmation, setShowRemoveConfirmation] = useState(false);
    const [actionError, setActionError] = useState<string | null>(null);

    const savings = calculateSavings(listing.originalPrice, listing.currentPrice);
    const discount = calculateDiscountPercentage(listing.originalPrice, listing.currentPrice);
    const purchased = listing.status === "PURCHASED";
    const purchasedForBookkeeping = listing.outcome === "PURCHASED_BY_ME"
        || (listing.outcome === null && purchased);

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

    async function saveOutcome(rawOutcome: string) {
        if (isSavingOutcome) {
            return;
        }

        const outcome = parseHistoryOutcome(rawOutcome);
        setIsSavingOutcome(true);
        setActionError(null);

        try {
            const updatedListing = await updateHistoryOutcome(listing.id, outcome);
            onUpdated(updatedListing);
        } catch (error) {
            setActionError(
                error instanceof Error
                    ? error.message
                    : "Nie udało się zmienić oznaczenia.",
            );
        } finally {
            setIsSavingOutcome(false);
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
                        <div className="history-card-badges">
                            <span
                                className={purchased
                                    ? "history-status history-status-purchased"
                                    : "history-status history-status-skipped"}
                            >
                                {purchased ? "✓ Kupione" : "✕ Odrzucone"}
                            </span>
                            {listing.outcome !== null && (
                                <span className="history-outcome-badge">
                                    {getHistoryOutcomeLabel(listing.outcome)}
                                </span>
                            )}
                        </div>
                        <h2>{listing.title}</h2>
                    </div>
                    <div className="history-decision-date">
                        <span>Data decyzji</span>
                        <strong>{formatDecisionDate(listing.decisionAt)}</strong>
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
                        <span>{purchasedForBookkeeping ? "Cena zakupu" : "Cena po negocjacji"}</span>
                        {purchasedForBookkeeping && editingPurchasePrice ? (
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
                    <HistoryDetail label="Listing ID" value={listing.listingId} />
                    <HistoryDetail label="Krok negocjacji" value={String(listing.currentStep)} />
                    <HistoryDetail label="Status techniczny" value={listing.status} />
                </div>

                <div className="history-outcome-editor">
                    <label htmlFor={`history-outcome-${listing.id}`}>
                        Oznaczenie do statystyk
                    </label>
                    <div className="history-outcome-editor-row">
                        <select
                            id={`history-outcome-${listing.id}`}
                            value={listing.outcome ?? ""}
                            disabled={isSavingOutcome || isRemoving}
                            onChange={(event) => void saveOutcome(event.target.value)}
                        >
                            <option value="">Brak oznaczenia</option>
                            {HISTORY_OUTCOME_OPTIONS.map(option => (
                                <option key={option.value} value={option.value}>
                                    {option.label}
                                </option>
                            ))}
                        </select>
                        {isSavingOutcome && <small>Zapisywanie...</small>}
                    </div>
                    <small>
                        Możesz zmieniać to oznaczenie także dla starych wpisów bez zmiany technicznego statusu bota.
                    </small>
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
                {purchasedForBookkeeping && !editingPurchasePrice && (
                    <button
                        className="secondary-button"
                        type="button"
                        disabled={isRemoving || isSavingOutcome}
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
                    disabled={isRemoving || isSaving || isSavingOutcome}
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
