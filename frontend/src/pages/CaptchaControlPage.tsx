import {
    useCallback,
    useEffect,
    useMemo,
    useRef,
    useState,
    type PointerEvent as ReactPointerEvent,
} from "react";

import {
    endCaptchaHold,
    getCaptchaControlState,
    getRuntimeDashboard,
    heartbeatCaptchaHold,
    requestCaptchaRecovery,
    startCaptchaHold,
    type CaptchaControlState,
    type RuntimeDashboardBot,
} from "../api/dashboardApi";

const RUNTIME_REFRESH_MS = 3_000;
const CONTROL_REFRESH_MS = 500;
const HOLD_HEARTBEAT_MS = 180;

function CaptchaControlPage() {
    const [bots, setBots] = useState<RuntimeDashboardBot[]>([]);
    const [activeBotId, setActiveBotId] = useState<number | null>(null);
    const [controlState, setControlState] = useState<CaptchaControlState | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [isPreparing, setIsPreparing] = useState<number | null>(null);
    const [isHolding, setIsHolding] = useState(false);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);

    const heartbeatTimerRef = useRef<number | null>(null);
    const pointerPressedRef = useRef(false);

    const loadRuntime = useCallback(async () => {
        try {
            const response = await getRuntimeDashboard();
            setBots(response.bots);
            setErrorMessage(null);
        } catch (error) {
            setErrorMessage(messageFrom(error, "Nie udało się pobrać kolejki CAPTCHA."));
        } finally {
            setIsLoading(false);
        }
    }, []);

    useEffect(() => {
        void loadRuntime();
        const timer = window.setInterval(() => {
            void loadRuntime();
        }, RUNTIME_REFRESH_MS);

        return () => window.clearInterval(timer);
    }, [loadRuntime]);

    const captchaBots = useMemo(
        () => bots.filter(bot => bot.captchaRequiredSince !== null),
        [bots],
    );

    const activeBot = useMemo(
        () => bots.find(bot => bot.botId === activeBotId) ?? null,
        [activeBotId, bots],
    );

    useEffect(() => {
        if (activeBotId !== null || captchaBots.length === 0) {
            return;
        }

        setActiveBotId(captchaBots[0].botId);
    }, [activeBotId, captchaBots]);

    const loadControlState = useCallback(async (botId: number) => {
        try {
            const state = await getCaptchaControlState(botId);
            setControlState(state);
            setErrorMessage(null);
        } catch (error) {
            setErrorMessage(messageFrom(error, "Nie udało się pobrać stanu sterowania CAPTCHA."));
        }
    }, []);

    useEffect(() => {
        if (activeBotId === null) {
            setControlState(null);
            return;
        }

        void loadControlState(activeBotId);
        const timer = window.setInterval(() => {
            void loadControlState(activeBotId);
        }, CONTROL_REFRESH_MS);

        return () => window.clearInterval(timer);
    }, [activeBotId, loadControlState]);

    const stopHeartbeat = useCallback(() => {
        if (heartbeatTimerRef.current !== null) {
            window.clearInterval(heartbeatTimerRef.current);
            heartbeatTimerRef.current = null;
        }
    }, []);

    const releaseHold = useCallback(async () => {
        pointerPressedRef.current = false;
        stopHeartbeat();
        setIsHolding(false);

        if (activeBotId === null) {
            return;
        }

        try {
            const state = await endCaptchaHold(activeBotId);
            setControlState(state);
        } catch (error) {
            setErrorMessage(messageFrom(error, "Nie udało się zatrzymać gestu CAPTCHA."));
        }
    }, [activeBotId, stopHeartbeat]);

    useEffect(() => {
        return () => {
            pointerPressedRef.current = false;
            stopHeartbeat();
            if (activeBotId !== null && isHolding) {
                void endCaptchaHold(activeBotId);
            }
        };
    }, [activeBotId, isHolding, stopHeartbeat]);

    const prepareBot = useCallback(async (bot: RuntimeDashboardBot) => {
        setActiveBotId(bot.botId);
        setIsPreparing(bot.botId);
        setErrorMessage(null);

        try {
            if (bot.captchaRecoveryRequestedAt === null) {
                await requestCaptchaRecovery(bot.botId);
            }
            await loadControlState(bot.botId);
        } catch (error) {
            setErrorMessage(messageFrom(error, "Nie udało się przygotować przeglądarki CAPTCHA."));
        } finally {
            setIsPreparing(null);
        }
    }, [loadControlState]);

    const beginHold = useCallback(async (
        event: ReactPointerEvent<HTMLButtonElement>,
    ) => {
        if (activeBotId === null
            || controlState?.status !== "READY"
            || pointerPressedRef.current) {
            return;
        }

        event.preventDefault();
        event.currentTarget.setPointerCapture(event.pointerId);
        pointerPressedRef.current = true;
        setErrorMessage(null);

        try {
            const state = await startCaptchaHold(activeBotId);
            setControlState(state);

            if (!pointerPressedRef.current) {
                await endCaptchaHold(activeBotId);
                return;
            }

            setIsHolding(true);
            stopHeartbeat();
            heartbeatTimerRef.current = window.setInterval(() => {
                void heartbeatCaptchaHold(activeBotId).catch(error => {
                    setErrorMessage(messageFrom(error, "Utracono połączenie sterowania CAPTCHA."));
                    void releaseHold();
                });
            }, HOLD_HEARTBEAT_MS);
        } catch (error) {
            pointerPressedRef.current = false;
            setIsHolding(false);
            setErrorMessage(messageFrom(error, "Nie udało się rozpocząć gestu CAPTCHA."));
        }
    }, [
        activeBotId,
        controlState?.status,
        releaseHold,
        stopHeartbeat,
    ]);

    const ready = controlState?.status === "READY";
    const preparing = controlState?.status === "PREPARING"
        || (activeBot?.captchaRecoveryRequestedAt !== null
            && controlState?.status !== "READY"
            && controlState?.status !== "HOLDING"
            && controlState?.status !== "COMPLETED"
            && controlState?.status !== "FAILED");

    return (
        <section className="page captcha-control-page">
            <header className="page-header">
                <div>
                    <p className="page-eyebrow">Human verification</p>
                    <h1 className="page-title">CAPTCHA</h1>
                    <p className="page-description">
                        Komputer otwiera właściwą sesję Vinted. Na telefonie tylko przygotowujesz
                        sesję i trzymasz przycisk, aby wykonać ręcznie sterowany ruch slidera.
                    </p>
                </div>
            </header>

            {errorMessage !== null && (
                <div className="form-message form-message-error" role="alert">
                    {errorMessage}
                </div>
            )}

            <div className="captcha-mobile-layout">
                <article className="content-card captcha-queue-card">
                    <div className="captcha-section-heading">
                        <div>
                            <h2 className="content-card-title">Kolejka</h2>
                            <p className="content-card-text">
                                {captchaBots.length === 0
                                    ? "Brak botów oczekujących na CAPTCHA."
                                    : `${captchaBots.length} bot${captchaBots.length === 1 ? "" : "ów"} oczekuje na reakcję.`}
                            </p>
                        </div>
                        <span className="captcha-queue-count">{captchaBots.length}</span>
                    </div>

                    {isLoading && captchaBots.length === 0 ? (
                        <div className="dictionary-list-state">Pobieranie kolejki...</div>
                    ) : captchaBots.length === 0 ? (
                        <div className="captcha-empty-state">
                            <span className="captcha-empty-icon" aria-hidden="true">✓</span>
                            <strong>Wszystko działa</strong>
                            <span>Żaden bot nie czeka teraz na ręczną weryfikację.</span>
                        </div>
                    ) : (
                        <div className="captcha-queue-list">
                            {captchaBots.map(bot => (
                                <button
                                    key={bot.botId}
                                    className={`captcha-queue-item ${activeBotId === bot.botId ? "captcha-queue-item-active" : ""}`.trim()}
                                    type="button"
                                    onClick={() => {
                                        setActiveBotId(bot.botId);
                                    }}
                                >
                                    <span className="captcha-queue-main">
                                        <strong>{bot.name}</strong>
                                        <span>#{bot.botId} · czeka {formatElapsed(bot.captchaRequiredSince)}</span>
                                    </span>
                                    <span className="captcha-queue-status">
                                        {bot.captchaRecoveryRequestedAt === null
                                            ? "OCZEKUJE"
                                            : bot.runtimeStatus === "WORKING"
                                                ? "OTWARTE"
                                                : "PRZYGOTOWANIE"}
                                    </span>
                                </button>
                            ))}
                        </div>
                    )}
                </article>

                <article className="content-card captcha-controller-card">
                    {activeBot === null ? (
                        <div className="captcha-empty-state captcha-controller-empty">
                            <span className="captcha-empty-icon" aria-hidden="true">→</span>
                            <strong>Wybierz bota</strong>
                            <span>Po wybraniu przygotujemy jego widoczną sesję na komputerze.</span>
                        </div>
                    ) : (
                        <>
                            <div className="captcha-controller-header">
                                <div>
                                    <p className="captcha-controller-kicker">Aktywna sesja</p>
                                    <h2>{activeBot.name}</h2>
                                    <span>Bot #{activeBot.botId}</span>
                                </div>
                                <CaptchaStateBadge state={controlState} />
                            </div>

                            {activeBot.captchaRecoveryRequestedAt === null
                                && controlState?.status !== "READY"
                                && controlState?.status !== "HOLDING" ? (
                                <button
                                    className="captcha-prepare-button"
                                    type="button"
                                    disabled={isPreparing === activeBot.botId}
                                    onClick={() => {
                                        void prepareBot(activeBot);
                                    }}
                                >
                                    {isPreparing === activeBot.botId
                                        ? "Przygotowywanie..."
                                        : "Rozwiąż CAPTCHA"}
                                </button>
                            ) : (
                                <div className="captcha-control-stage">
                                    <div className="captcha-control-progress">
                                        <span className={`captcha-control-dot captcha-control-dot-${controlState?.status?.toLowerCase() ?? "idle"}`} />
                                        <div>
                                            <strong>{controlTitle(controlState, preparing)}</strong>
                                            <span>{controlDescription(controlState, preparing)}</span>
                                        </div>
                                    </div>

                                    <button
                                        className={`captcha-hold-button ${isHolding ? "captcha-hold-button-active" : ""}`.trim()}
                                        type="button"
                                        disabled={!ready && !isHolding}
                                        onPointerDown={event => {
                                            void beginHold(event);
                                        }}
                                        onPointerUp={() => {
                                            void releaseHold();
                                        }}
                                        onPointerCancel={() => {
                                            void releaseHold();
                                        }}
                                        onLostPointerCapture={() => {
                                            if (pointerPressedRef.current) {
                                                void releaseHold();
                                            }
                                        }}
                                        onContextMenu={event => event.preventDefault()}
                                    >
                                        <span className="captcha-hold-arrow" aria-hidden="true">→</span>
                                        <span className="captcha-hold-label">
                                            {isHolding ? "TRZYMASZ — PRZESUWAM" : "TRZYMAJ →"}
                                        </span>
                                        <span className="captcha-hold-help">
                                            Puść palec, aby natychmiast zatrzymać ruch
                                        </span>
                                    </button>

                                    <div className="captcha-safety-note">
                                        Ruch działa wyłącznie podczas aktywnego trzymania. Brak heartbeat z telefonu
                                        automatycznie puszcza mysz na komputerze.
                                    </div>
                                </div>
                            )}
                        </>
                    )}
                </article>
            </div>
        </section>
    );
}

function CaptchaStateBadge({ state }: { state: CaptchaControlState | null }) {
    const status = state?.status ?? "IDLE";
    return (
        <span className={`captcha-control-badge captcha-control-badge-${status.toLowerCase()}`}>
            {status}
        </span>
    );
}

function controlTitle(
    state: CaptchaControlState | null,
    preparing: boolean,
): string {
    if (state?.status === "READY") return "CAPTCHA gotowa";
    if (state?.status === "HOLDING") return "Ruch trwa";
    if (state?.status === "COMPLETED") return "CAPTCHA zaakceptowana";
    if (state?.status === "FAILED") return "Sesja wymaga ponowienia";
    if (preparing) return "Otwieranie przeglądarki na komputerze";
    return "Oczekiwanie";
}

function controlDescription(
    state: CaptchaControlState | null,
    preparing: boolean,
): string {
    if (state?.status === "READY") {
        return "Przytrzymaj duży przycisk poniżej. Komputer wykona ruch tylko podczas trzymania.";
    }
    if (state?.status === "HOLDING") {
        return "Telefon wysyła heartbeat, a headed Chromium przesuwa aktualny slider.";
    }
    if (state?.status === "COMPLETED") {
        return "Sesja zostanie zapisana do bot-X.json, a bot wróci do normalnego harmonogramu.";
    }
    if (state?.status === "FAILED") {
        return state.message ?? "Przeglądarka została zamknięta albo challenge nie został ukończony.";
    }
    if (preparing) {
        return "LoginService dochodzi do miejsca, w którym challenge faktycznie jest widoczny.";
    }
    return "Kliknij „Rozwiąż CAPTCHA”, aby otworzyć właściwą sesję.";
}

function formatElapsed(value: string | null): string {
    if (value === null) return "—";
    const timestamp = Date.parse(value);
    if (!Number.isFinite(timestamp)) return "—";

    const seconds = Math.max(0, Math.floor((Date.now() - timestamp) / 1_000));
    if (seconds < 60) return `${seconds}s`;
    const minutes = Math.floor(seconds / 60);
    if (minutes < 60) return `${minutes} min`;
    const hours = Math.floor(minutes / 60);
    return `${hours} godz.`;
}

function messageFrom(error: unknown, fallback: string): string {
    return error instanceof Error ? error.message : fallback;
}

export default CaptchaControlPage;
