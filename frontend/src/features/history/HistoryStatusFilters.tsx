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
    skippedCount: number;
    onChange: (value: HistoryFilter) => void;
}

function HistoryStatusFilters({
    value,
    totalCount,
    purchasedCount,
    skippedCount,
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
                active={value === "SKIPPED_BY_USER"}
                label="Odrzucone"
                count={skippedCount}
                onClick={() => onChange("SKIPPED_BY_USER")}
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
