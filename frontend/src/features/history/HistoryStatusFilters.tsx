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
    unclassifiedCount: number;
    onChange: (value: HistoryFilter) => void;
}

function HistoryStatusFilters({
    value,
    totalCount,
    purchasedCount,
    rejectedCount,
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
                label="Kupiłem"
                count={purchasedCount}
                onClick={() => onChange("PURCHASED")}
            />
            <HistoryFilterButton
                active={value === "REJECTED"}
                label="Nie kupiłem"
                count={rejectedCount}
                onClick={() => onChange("REJECTED")}
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