import type {
    HistoryFilter,
} from "./historyTypes";
import {
    getHistoryFilterClassName,
} from "./historyUtils";

interface HistoryStatusFiltersProps {
    value: HistoryFilter;
    totalCount: number;
    purchasedCount: number;
    rejectedCount: number;
    missedCount: number;
    unclassifiedCount: number;
    onChange: (value: HistoryFilter) => void;
}

function HistoryStatusFilters({
    value,
    totalCount,
    purchasedCount,
    rejectedCount,
    missedCount,
    unclassifiedCount,
    onChange,
}: HistoryStatusFiltersProps) {
    return (
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
                active={value === "REJECTED"}
                label="Odrzucone"
                count={rejectedCount}
                onClick={() => onChange("REJECTED")}
            />
            <HistoryFilterButton
                active={value === "MISSED_OPPORTUNITY"}
                label="Utracone okazje"
                count={missedCount}
                onClick={() => onChange("MISSED_OPPORTUNITY")}
            />
            <HistoryFilterButton
                active={value === "UNCLASSIFIED"}
                label="Do oznaczenia"
                count={unclassifiedCount}
                onClick={() => onChange("UNCLASSIFIED")}
            />
        </div>
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
