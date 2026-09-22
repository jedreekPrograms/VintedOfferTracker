import type {
    HistoryAssessmentFilter,
} from "./historyTypes";
import {
    getHistoryFilterClassName,
} from "./historyUtils";

interface Props {
    value: HistoryAssessmentFilter;
    legitCount: number;
    scamCount: number;
    unassessedCount: number;
    onChange: (value: HistoryAssessmentFilter) => void;
}

function HistoryAssessmentFilters({
    value,
    legitCount,
    scamCount,
    unassessedCount,
    onChange,
}: Props) {
    return (
        <div className="history-assessment-section">
            <span className="history-assessment-label">Ocena oferty</span>
            <div className="history-filters">
                <button
                    className={getHistoryFilterClassName(value === "ALL")}
                    type="button"
                    onClick={() => onChange("ALL")}
                >
                    Wszystkie oceny
                </button>
                <button
                    className={getHistoryFilterClassName(value === "LEGIT")}
                    type="button"
                    onClick={() => onChange("LEGIT")}
                >
                    Legit <span>{legitCount}</span>
                </button>
                <button
                    className={getHistoryFilterClassName(value === "SCAM")}
                    type="button"
                    onClick={() => onChange("SCAM")}
                >
                    Oszustwo <span>{scamCount}</span>
                </button>
                <button
                    className={getHistoryFilterClassName(value === "UNASSESSED")}
                    type="button"
                    onClick={() => onChange("UNASSESSED")}
                >
                    Nieocenione <span>{unassessedCount}</span>
                </button>
            </div>
        </div>
    );
}

export default HistoryAssessmentFilters;
