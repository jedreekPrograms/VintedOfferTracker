import {
    type ReactNode,
    useEffect,
    useId,
    useRef,
} from "react";
import { createPortal } from "react-dom";

import "../styles/app-dialog.css";

interface AppDialogInput {
    label: string;
    value: string;
    placeholder?: string;
    errorMessage?: string | null;
    onChange: (value: string) => void;
}

interface AppDialogProps {
    open: boolean;
    title: string;
    description: ReactNode;
    confirmLabel?: string;
    cancelLabel?: string;
    danger?: boolean;
    busy?: boolean;
    input?: AppDialogInput;
    onConfirm: () => void;
    onCancel: () => void;
}

function AppDialog({
    open,
    title,
    description,
    confirmLabel = "Potwierdź",
    cancelLabel = "Anuluj",
    danger = false,
    busy = false,
    input,
    onConfirm,
    onCancel,
}: AppDialogProps) {
    const titleId = useId();
    const descriptionId = useId();
    const inputId = useId();
    const inputRef = useRef<HTMLInputElement>(null);
    const cancelRef = useRef<HTMLButtonElement>(null);
    const onCancelRef = useRef(onCancel);
    const busyRef = useRef(busy);
    const hasInput = input !== undefined;

    useEffect(() => {
        onCancelRef.current = onCancel;
    }, [onCancel]);

    useEffect(() => {
        busyRef.current = busy;
    }, [busy]);

    useEffect(() => {
        if (!open) {
            return;
        }

        const previousOverflow = document.body.style.overflow;
        document.body.style.overflow = "hidden";

        const animationFrame = window.requestAnimationFrame(() => {
            if (hasInput) {
                inputRef.current?.focus();
                inputRef.current?.select();
            } else {
                cancelRef.current?.focus();
            }
        });

        function handleKeyDown(event: KeyboardEvent) {
            if (event.key === "Escape" && !busyRef.current) {
                event.preventDefault();
                onCancelRef.current();
            }
        }

        document.addEventListener("keydown", handleKeyDown);

        return () => {
            window.cancelAnimationFrame(animationFrame);
            document.removeEventListener("keydown", handleKeyDown);
            document.body.style.overflow = previousOverflow;
        };
    }, [hasInput, open]);

    if (!open) {
        return null;
    }

    return createPortal(
        <div
            className="app-dialog-backdrop"
            onMouseDown={(event) => {
                if (event.target === event.currentTarget && !busy) {
                    onCancel();
                }
            }}
        >
            <form
                className="app-dialog"
                role="dialog"
                aria-modal="true"
                aria-labelledby={titleId}
                aria-describedby={descriptionId}
                onSubmit={(event) => {
                    event.preventDefault();
                    if (!busy) {
                        onConfirm();
                    }
                }}
            >
                <div className="app-dialog-heading">
                    <div className={danger ? "app-dialog-icon app-dialog-icon-danger" : "app-dialog-icon"}>
                        {danger ? "!" : "✓"}
                    </div>
                    <div>
                        <h2 id={titleId}>{title}</h2>
                        <div id={descriptionId} className="app-dialog-description">
                            {description}
                        </div>
                    </div>
                </div>

                {input !== undefined && (
                    <div className="app-dialog-field">
                        <label htmlFor={inputId}>{input.label}</label>
                        <input
                            ref={inputRef}
                            id={inputId}
                            className="form-input"
                            value={input.value}
                            placeholder={input.placeholder}
                            disabled={busy}
                            onChange={(event) => input.onChange(event.target.value)}
                        />
                        {input.errorMessage !== null
                            && input.errorMessage !== undefined
                            && (
                                <span className="app-dialog-field-error" role="alert">
                                    {input.errorMessage}
                                </span>
                            )}
                    </div>
                )}

                <div className="app-dialog-actions">
                    <button
                        ref={cancelRef}
                        className="secondary-button"
                        type="button"
                        disabled={busy}
                        onClick={onCancel}
                    >
                        {cancelLabel}
                    </button>
                    <button
                        className={danger ? "bot-stop-button app-dialog-confirm" : "primary-button app-dialog-confirm"}
                        type="submit"
                        disabled={busy}
                    >
                        {busy ? "Proszę czekać..." : confirmLabel}
                    </button>
                </div>
            </form>
        </div>,
        document.body,
    );
}

export default AppDialog;
