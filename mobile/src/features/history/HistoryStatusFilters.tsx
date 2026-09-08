import type {
    HistoryFilter,
    HistoryOutcomeFilter,
} from "./historyTypes";
import {
    getHistoryFilterClassName,
} from "./historyUtils";

interface HistoryStatusFiltersProps {
    value: HistoryFilter;
    outcomeValue: HistoryOutcomeFilter;
    totalCount: number;
    purchasedCount: number;
    skippedCount: number;
    purchasedByMeCount: number;
    soldToOtherCount: number;
    scamCount: number;
    phoneLockedCount: number;
    otherCount: number;
    unclassifiedCount: number;
    onChange: (value: HistoryFilter) => void;
    onOutcomeChange: (value: HistoryOutcomeFilter) => void;
}

function HistoryStatusFilters({
    value,
    outcomeValue,
    totalCount,
    purchasedCount,
    skippedCount,
    purchasedByMeCount,
    soldToOtherCount,
    scamCount,
    phoneLockedCount,
    otherCount,
    unclassifiedCount,
    onChange,
    onOutcomeChange,
}: HistoryStatusFiltersProps) {
    return (
        <>
            <div className="history-filters">
                <HistoryFilterButton
                    active={value === "ALL"}
                    label="Wszystkie"
                    count={totalCount}
                    onClick={() => onChange("ALL")}
                />
                <HistoryFilterButton
                    active={value === "PURCHASED"}
                    label="Kupione"
                    count={purchasedCount}
                    onClick={() => onChange("PURCHASED")}
                />
                <HistoryFilterButton
                    active={value === "SKIPPED_BY_USER"}
                    label="Odrzucone"
                    count={skippedCount}
                    onClick={() => onChange("SKIPPED_BY_USER")}
                />
            </div>

            <div className="history-outcome-filter-section">
                <span className="history-outcome-filter-title">
                    Oznaczenia do statystyk
                </span>
                <div className="history-filters history-outcome-filters">
                    <HistoryFilterButton
                        active={outcomeValue === "ALL"}
                        label="Wszystkie oznaczenia"
                        count={totalCount}
                        onClick={() => onOutcomeChange("ALL")}
                    />
                    <HistoryFilterButton
                        active={outcomeValue === "PURCHASED_BY_ME"}
                        label="Kupiłem"
                        count={purchasedByMeCount}
                        onClick={() => onOutcomeChange("PURCHASED_BY_ME")}
                    />
                    <HistoryFilterButton
                        active={outcomeValue === "SOLD_TO_OTHER"}
                        label="Ktoś kupił przede mną"
                        count={soldToOtherCount}
                        onClick={() => onOutcomeChange("SOLD_TO_OTHER")}
                    />
                    <HistoryFilterButton
                        active={outcomeValue === "SCAM"}
                        label="Oszustwo"
                        count={scamCount}
                        onClick={() => onOutcomeChange("SCAM")}
                    />
                    <HistoryFilterButton
                        active={outcomeValue === "PHONE_LOCKED"}
                        label="Telefon z blokadą"
                        count={phoneLockedCount}
                        onClick={() => onOutcomeChange("PHONE_LOCKED")}
                    />
                    <HistoryFilterButton
                        active={outcomeValue === "OTHER"}
                        label="Inne / ogólne"
                        count={otherCount}
                        onClick={() => onOutcomeChange("OTHER")}
                    />
                    <HistoryFilterButton
                        active={outcomeValue === "UNCLASSIFIED"}
                        label="Nieoznaczone"
                        count={unclassifiedCount}
                        onClick={() => onOutcomeChange("UNCLASSIFIED")}
                    />
                </div>
            </div>
        </>
    );
}

interface HistoryFilterButtonProps {
    active: boolean;
    label: string;
    count: number;
    onClick: () => void;
}

function HistoryFilterButton({
    active,
    label,
    count,
    onClick,
}: HistoryFilterButtonProps) {
    return (
        <button
            className={getHistoryFilterClassName(active)}
            type="button"
            onClick={onClick}
        >
            {label}
            <span>{count}</span>
        </button>
    );
}

export default HistoryStatusFilters;
