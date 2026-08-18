import {
    type CSSProperties,
    useCallback,
    useEffect,
    useLayoutEffect,
    useRef,
    useState,
} from "react";
import { createPortal } from "react-dom";

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

interface MenuPosition {
    left: number;
    width: number;
    maxHeight: number;
    top?: number;
    bottom?: number;
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
    const [menuPosition, setMenuPosition] = useState<MenuPosition | null>(null);
    const rootRef = useRef<HTMLDivElement | null>(null);
    const triggerRef = useRef<HTMLButtonElement | null>(null);
    const menuRef = useRef<HTMLDivElement | null>(null);

    const isExpanded = isOpen && !disabled;

    const selectedOption = options.find(
        option => option.value === value,
    ) ?? null;

    const updateMenuPosition = useCallback(() => {
        const trigger = triggerRef.current;

        if (trigger === null) {
            return;
        }

        const rect = trigger.getBoundingClientRect();
        const viewportPadding = 10;
        const preferredHeight = 320;
        const gap = 7;
        const spaceBelow = window.innerHeight - rect.bottom - viewportPadding;
        const spaceAbove = rect.top - viewportPadding;
        const openUp = spaceBelow < 220 && spaceAbove > spaceBelow;
        const availableSpace = openUp ? spaceAbove : spaceBelow;
        const maxHeight = Math.max(
            96,
            Math.min(preferredHeight, Math.max(96, availableSpace - gap)),
        );
        const safeWidth = Math.min(
            rect.width,
            Math.max(0, window.innerWidth - viewportPadding * 2),
        );
        const safeLeft = Math.max(
            viewportPadding,
            Math.min(
                rect.left,
                window.innerWidth - safeWidth - viewportPadding,
            ),
        );

        setMenuPosition({
            left: safeLeft,
            width: safeWidth,
            maxHeight,
            ...(openUp
                ? {
                    bottom: window.innerHeight - rect.top + gap,
                }
                : {
                    top: rect.bottom + gap,
                }),
        });
    }, []);

    useLayoutEffect(() => {
        if (!isExpanded) {
            return;
        }

        updateMenuPosition();

        const handleViewportChange = () => {
            updateMenuPosition();
        };

        window.addEventListener("resize", handleViewportChange);
        window.addEventListener("scroll", handleViewportChange, true);

        return () => {
            window.removeEventListener("resize", handleViewportChange);
            window.removeEventListener("scroll", handleViewportChange, true);
        };
    }, [isExpanded, updateMenuPosition]);

    useEffect(() => {
        if (!isExpanded) {
            return;
        }

        const closeOnOutsidePress = (event: PointerEvent) => {
            const target = event.target;

            if (!(target instanceof Node)) {
                return;
            }

            if (
                rootRef.current?.contains(target)
                || menuRef.current?.contains(target)
            ) {
                return;
            }

            setIsOpen(false);
        };

        const closeOnEscape = (event: KeyboardEvent) => {
            if (event.key === "Escape") {
                setIsOpen(false);
                triggerRef.current?.focus();
            }
        };

        window.addEventListener("pointerdown", closeOnOutsidePress);
        window.addEventListener("keydown", closeOnEscape);

        return () => {
            window.removeEventListener("pointerdown", closeOnOutsidePress);
            window.removeEventListener("keydown", closeOnEscape);
        };
    }, [isExpanded]);

    const menuStyle: CSSProperties | undefined = menuPosition === null
        ? undefined
        : {
            left: menuPosition.left,
            width: menuPosition.width,
            maxHeight: menuPosition.maxHeight,
            top: menuPosition.top ?? "auto",
            bottom: menuPosition.bottom ?? "auto",
        };

    return (
        <div
            ref={rootRef}
            className={`app-select ${isExpanded ? "app-select-open" : ""} ${disabled ? "app-select-disabled" : ""} ${className}`.trim()}
        >
            <button
                ref={triggerRef}
                id={id}
                className="app-select-trigger"
                type="button"
                aria-label={ariaLabel}
                aria-haspopup="listbox"
                aria-expanded={isExpanded}
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

            {isExpanded
                && menuPosition !== null
                && createPortal(
                    <div
                        ref={menuRef}
                        className="app-select-menu app-select-menu-portal"
                        role="listbox"
                        aria-label={ariaLabel}
                        style={menuStyle}
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
                                    triggerRef.current?.focus();
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
                    </div>,
                    document.body,
                )}
        </div>
    );
}

export default AppSelect;
