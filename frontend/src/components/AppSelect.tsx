import {
    useEffect,
    useRef,
    useState,
} from "react";

export interface AppSelectOption {
    value: string;
    label: string;
    disabled?: boolean;
}

interface AppSelectProps {
    id?: string;
    value: string;
    options: AppSelectOption[];
    onChange: (value: string) => void;
    disabled?: boolean;
    placeholder?: string;
    ariaLabel?: string;
    className?: string;
}

function AppSelect({
    id,
    value,
    options,
    onChange,
    disabled = false,
    placeholder = "Wybierz",
    ariaLabel,
    className = "",
}: AppSelectProps) {
    const [isOpen, setIsOpen] = useState(false);
    const rootRef = useRef<HTMLDivElement | null>(null);

    const selectedOption = options.find(
        option => option.value === value,
    ) ?? null;

    useEffect(() => {
        if (!isOpen) {
            return;
        }

        const closeOnOutsidePress = (event: PointerEvent) => {
            const target = event.target;

            if (
                target instanceof Node
                && !rootRef.current?.contains(target)
            ) {
                setIsOpen(false);
            }
        };

        const closeOnEscape = (event: KeyboardEvent) => {
            if (event.key === "Escape") {
                setIsOpen(false);
            }
        };

        window.addEventListener("pointerdown", closeOnOutsidePress);
        window.addEventListener("keydown", closeOnEscape);

        return () => {
            window.removeEventListener("pointerdown", closeOnOutsidePress);
            window.removeEventListener("keydown", closeOnEscape);
        };
    }, [isOpen]);

    useEffect(() => {
        if (disabled) {
            setIsOpen(false);
        }
    }, [disabled]);

    return (
        <div
            ref={rootRef}
            className={`app-select ${isOpen ? "app-select-open" : ""} ${disabled ? "app-select-disabled" : ""} ${className}`.trim()}
        >
            <button
                id={id}
                className="app-select-trigger"
                type="button"
                aria-label={ariaLabel}
                aria-haspopup="listbox"
                aria-expanded={isOpen}
                disabled={disabled}
                onClick={() => setIsOpen(current => !current)}
            >
                <span
                    className={selectedOption === null
                        ? "app-select-value app-select-placeholder"
                        : "app-select-value"}
                >
                    {selectedOption?.label ?? placeholder}
                </span>

                <span
                    className="app-select-chevron"
                    aria-hidden="true"
                />
            </button>

            {isOpen && (
                <div
                    className="app-select-menu"
                    role="listbox"
                    aria-label={ariaLabel}
                >
                    {options.map(option => (
                        <button
                            key={option.value}
                            className={`app-select-option ${option.value === value ? "app-select-option-selected" : ""}`.trim()}
                            type="button"
                            role="option"
                            aria-selected={option.value === value}
                            disabled={option.disabled}
                            onClick={() => {
                                if (option.disabled) {
                                    return;
                                }

                                onChange(option.value);
                                setIsOpen(false);
                            }}
                        >
                            <span className="app-select-option-label">
                                {option.label}
                            </span>

                            {option.value === value && (
                                <span
                                    className="app-select-check"
                                    aria-hidden="true"
                                >
                                    ✓
                                </span>
                            )}
                        </button>
                    ))}
                </div>
            )}
        </div>
    );
}

export default AppSelect;
