import {
    useCallback,
    useEffect,
    useRef,
    useState,
    type PointerEvent as ReactPointerEvent,
} from "react";

import {
    getRemoteCaptchaScreenshot,
    getRemoteCaptchaState,
    getRuntimeDashboard,
    requestCaptchaRecovery,
    sendRemoteCaptchaPointer,
    type RemoteCaptchaState,
    type RuntimeDashboardBot,
} from "../api/dashboardApi";

const RUNTIME_REFRESH_MS = 1_500;
const REMOTE_REFRESH_MS = 450;
const POINTER_MOVE_THROTTLE_MS = 35;

function CaptchaPage() {
    const [bots, setBots] = useState<RuntimeDashboardBot[]>([]);
    const [selectedBot, setSelectedBot] = useState<RuntimeDashboardBot | null>(null);
    const [remoteState, setRemoteState] = useState<RemoteCaptchaState | null>(null);
    const [frameUrl, setFrameUrl] = useState<string | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [startingBotId, setStartingBotId] = useState<number | null>(null);
    const lastLoadedFrame = useRef(0);

    const loadRuntime = useCallback(async () => {
        try {
            const data = await getRuntimeDashboard();
            const captchaBots = data.bots.filter(bot => bot.captchaRequiredSince !== null);
            setBots(captchaBots);
            setError(null);

            if (selectedBot !== null) {
                const fresh = captchaBots.find(bot => bot.botId === selectedBot.botId) ?? null;
                setSelectedBot(fresh);
                if (fresh === null) setRemoteState(null);
            }
        } catch (caught) {
            setError(caught instanceof Error ? caught.message : "Nie udało się pobrać kolejki CAPTCHA.");
        }
    }, [selectedBot]);

    useEffect(() => {
        void loadRuntime();
        const id = window.setInterval(() => void loadRuntime(), RUNTIME_REFRESH_MS);
        return () => window.clearInterval(id);
    }, [loadRuntime]);

    useEffect(() => {
        if (selectedBot === null) {
            setRemoteState(null);
            return undefined;
        }

        let cancelled = false;
        const botId = selectedBot.botId;
        const refresh = async () => {
            try {
                const state = await getRemoteCaptchaState(botId);
                if (cancelled) return;
                setRemoteState(state);

                if (state.active && state.screenshotAvailable && state.frameVersion !== lastLoadedFrame.current) {
                    const blob = await getRemoteCaptchaScreenshot(botId, state.frameVersion);
                    if (cancelled) return;
                    const nextUrl = URL.createObjectURL(blob);
                    setFrameUrl(previous => {
                        if (previous !== null) URL.revokeObjectURL(previous);
                        return nextUrl;
                    });
                    lastLoadedFrame.current = state.frameVersion;
                }
            } catch (caught) {
                if (!cancelled) {
                    setRemoteState(null);
                    setError(caught instanceof Error ? caught.message : "Zdalna sesja CAPTCHA nie jest jeszcze gotowa.");
                }
            }
        };

        void refresh();
        const id = window.setInterval(() => void refresh(), REMOTE_REFRESH_MS);
        return () => {
            cancelled = true;
            window.clearInterval(id);
        };
    }, [selectedBot]);

    useEffect(() => () => {
        if (frameUrl !== null) URL.revokeObjectURL(frameUrl);
    }, [frameUrl]);

    const open = async (bot: RuntimeDashboardBot) => {
        setSelectedBot(bot);
        setStartingBotId(bot.botId);
        lastLoadedFrame.current = 0;
        setError(null);
        try {
            if (bot.captchaRecoveryRequestedAt === null) await requestCaptchaRecovery(bot.botId);
        } catch (caught) {
            setError(caught instanceof Error ? caught.message : "Nie udało się uruchomić ręcznej sesji CAPTCHA.");
        } finally {
            setStartingBotId(null);
        }
    };

    return (
        <section className="page captcha-page">
            <header className="page-header">
                <div>
                    <p className="page-eyebrow">Ręczna weryfikacja</p>
                    <h1 className="page-title">CAPTCHA</h1>
                    <p className="page-description">Otwórz zatrzymaną sesję i wykonaj gest bezpośrednio na obrazie przeglądarki działającej na komputerze.</p>
                </div>
                <span className="captcha-queue-counter">{bots.length}</span>
            </header>

            {error !== null && <div className="form-message form-message-error" role="alert">{error}</div>}

            {selectedBot === null ? (
                <CaptchaQueue bots={bots} startingBotId={startingBotId} onOpen={open} />
            ) : (
                <RemoteCaptchaViewer
                    bot={selectedBot}
                    state={remoteState}
                    frameUrl={frameUrl}
                    onBack={() => {
                        setSelectedBot(null);
                        setRemoteState(null);
                        setFrameUrl(previous => {
                            if (previous !== null) URL.revokeObjectURL(previous);
                            return null;
                        });
                    }}
                />
            )}
        </section>
    );
}

function CaptchaQueue({ bots, startingBotId, onOpen }: {
    bots: RuntimeDashboardBot[];
    startingBotId: number | null;
    onOpen: (bot: RuntimeDashboardBot) => Promise<void>;
}) {
    if (bots.length === 0) {
        return (
            <article className="empty-state captcha-empty-state">
                <div className="captcha-success-icon">✓</div>
                <h2 className="empty-state-title">Brak oczekujących CAPTCHA</h2>
                <p className="empty-state-description">Wszystkie boty mogą obecnie wykonywać swoje normalne zadania.</p>
            </article>
        );
    }

    return (
        <div className="captcha-queue-list">
            {bots.map((bot, index) => (
                <article className="content-card captcha-queue-card" key={bot.botId}>
                    <div className="captcha-queue-order">{index + 1}</div>
                    <div className="captcha-queue-copy">
                        <strong>{bot.name}</strong>
                        <span>Bot #{bot.botId} · CAPTCHA REQUIRED</span>
                    </div>
                    <button className="primary-button captcha-solve-button" type="button" disabled={startingBotId === bot.botId} onClick={() => void onOpen(bot)}>
                        {startingBotId === bot.botId ? "Uruchamianie..." : "Rozwiąż"}
                    </button>
                </article>
            ))}
        </div>
    );
}

function RemoteCaptchaViewer({ bot, state, frameUrl, onBack }: {
    bot: RuntimeDashboardBot;
    state: RemoteCaptchaState | null;
    frameUrl: string | null;
    onBack: () => void;
}) {
    const lastMoveAt = useRef(0);
    const pointerActive = useRef(false);

    const coordinates = (event: ReactPointerEvent<HTMLDivElement>) => {
        const image = event.currentTarget.querySelector("img");
        const rect = image?.getBoundingClientRect() ?? event.currentTarget.getBoundingClientRect();
        return {
            x: Math.min(1, Math.max(0, (event.clientX - rect.left) / Math.max(1, rect.width))),
            y: Math.min(1, Math.max(0, (event.clientY - rect.top) / Math.max(1, rect.height))),
        };
    };

    const send = (type: "DOWN" | "MOVE" | "UP", event: ReactPointerEvent<HTMLDivElement>) => {
        const point = coordinates(event);
        void sendRemoteCaptchaPointer(bot.botId, type, point.x, point.y);
    };

    const pointerDown = (event: ReactPointerEvent<HTMLDivElement>) => {
        if (!state?.active || frameUrl === null) return;
        pointerActive.current = true;
        lastMoveAt.current = performance.now();
        event.currentTarget.setPointerCapture(event.pointerId);
        send("DOWN", event);
    };

    const pointerMove = (event: ReactPointerEvent<HTMLDivElement>) => {
        if (!pointerActive.current) return;
        const now = performance.now();
        if (now - lastMoveAt.current < POINTER_MOVE_THROTTLE_MS) return;
        lastMoveAt.current = now;
        send("MOVE", event);
    };

    const pointerUp = (event: ReactPointerEvent<HTMLDivElement>) => {
        if (!pointerActive.current) return;
        pointerActive.current = false;
        send("UP", event);
        if (event.currentTarget.hasPointerCapture(event.pointerId)) event.currentTarget.releasePointerCapture(event.pointerId);
    };

    return (
        <article className="content-card captcha-remote-card">
            <div className="captcha-remote-header">
                <div>
                    <button className="captcha-back-button" type="button" onClick={onBack}>← Kolejka</button>
                    <h2>{bot.name}</h2>
                    <p>Bot #{bot.botId}</p>
                </div>
                <span className={`captcha-live-badge ${state?.active ? "captcha-live-badge-online" : ""}`}>
                    {state?.active ? "SESJA GOTOWA" : "PRZYGOTOWYWANIE"}
                </span>
            </div>

            <div
                className={`captcha-remote-viewport ${state?.active ? "captcha-remote-viewport-live" : ""}`}
                onPointerDown={pointerDown}
                onPointerMove={pointerMove}
                onPointerUp={pointerUp}
                onPointerCancel={pointerUp}
            >
                {frameUrl !== null ? (
                    <img src={frameUrl} alt="Aktualny widok przeglądarki CAPTCHA" draggable={false} />
                ) : (
                    <div className="captcha-remote-loading">
                        <span className="captcha-spinner" />
                        <strong>Przygotowywanie przeglądarki na komputerze</strong>
                        <p>FlipBot otwiera tę samą sesję bota i przechodzi do właściwego challenge'a.</p>
                    </div>
                )}
            </div>

            <div className="captcha-gesture-help">
                <strong>Gest z telefonu steruje kursorem 1:1</strong>
                <span>Dotknij uchwytu na obrazie, przeciągnij palcem i puść. FlipBot nie wybiera ruchu za Ciebie.</span>
            </div>
        </article>
    );
}

export default CaptchaPage;
