import type {
    ListingHistoryResponse,
} from "../../api/historyApi";
import {
    calculateDiscountPercentage,
    calculateSavings,
    formatDecisionDate,
    formatHistoryPercentage,
    formatHistoryPrice,
    getAbsoluteVintedUrl,
} from "./historyUtils";

interface HistoryCardProps {
    listing: ListingHistoryResponse;
}

function HistoryCard({ listing }: HistoryCardProps) {
    const savings = calculateSavings(
        listing.originalPrice,
        listing.currentPrice,
    );
    const discount = calculateDiscountPercentage(
        listing.originalPrice,
        listing.currentPrice,
    );
    const purchased = listing.status === "PURCHASED";

    return (
        <article className="history-card">
            <div className="history-card-main">
                <div className="history-card-header">
                    <div>
                        <span
                            className={purchased
                                ? "history-status history-status-purchased"
                                : "history-status history-status-skipped"}
                        >
                            {purchased ? "✓ Kupione" : "✕ Odrzucone"}
                        </span>
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
                        <span>Cena po negocjacji</span>
                        <strong className="history-current-price">
                            {formatHistoryPrice(listing.currentPrice)}
                        </strong>
                    </div>

                    <div className="history-saving">
                        <span>Wynegocjowano</span>
                        <strong>{formatHistoryPrice(savings)}</strong>
                        <small>-{formatHistoryPercentage(discount)}</small>
                    </div>
                </div>

                <div className="history-details">
                    <HistoryDetail
                        label="Bot"
                        value={listing.botName}
                        secondary={`#${listing.botId}`}
                    />
                    <HistoryDetail
                        label="Listing ID"
                        value={listing.listingId}
                    />
                    <HistoryDetail
                        label="Krok negocjacji"
                        value={String(listing.currentStep)}
                    />
                    <HistoryDetail
                        label="Status"
                        value={listing.status}
                    />
                </div>
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
            </div>
        </article>
    );
}

interface HistoryDetailProps {
    label: string;
    value: string;
    secondary?: string;
}

function HistoryDetail({
    label,
    value,
    secondary,
}: HistoryDetailProps) {
    return (
        <div>
            <span>{label}</span>
            <strong>{value}</strong>
            {secondary !== undefined && <small>{secondary}</small>}
        </div>
    );
}

export default HistoryCard;
