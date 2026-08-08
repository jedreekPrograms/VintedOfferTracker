import {
    useCallback,
    useEffect,
    useState,
} from "react";

import {
    Link,
} from "react-router-dom";

import {
    getBots,
    startBot,
    stopBot,
} from "../api/botsApi";

import type {
    BotListItem,
} from "../types/bots";

function BotsPage() {
    const [
        bots,
        setBots,
    ] = useState<BotListItem[]>([]);

    const [
        isLoading,
        setIsLoading,
    ] = useState(true);

    const [
        actionBotId,
        setActionBotId,
    ] = useState<number | null>(
        null,
    );

    const [
        errorMessage,
        setErrorMessage,
    ] = useState<string | null>(
        null,
    );

    const loadBots =
        useCallback(
            async () => {
                setIsLoading(
                    true,
                );

                setErrorMessage(
                    null,
                );

                try {
                    const loadedBots =
                        await getBots();

                    setBots(
                        loadedBots,
                    );
                } catch (error) {
                    setErrorMessage(
                        getErrorMessage(
                            error,
                            "Nie udało się pobrać botów.",
                        ),
                    );
                } finally {
                    setIsLoading(
                        false,
                    );
                }
            },
            [],
        );

    useEffect(() => {
        void loadBots();
    }, [
        loadBots,
    ]);

    async function handleStartBot(
        botId: number,
    ) {
        if (
            actionBotId !== null
        ) {
            return;
        }

        setActionBotId(
            botId,
        );

        setErrorMessage(
            null,
        );

        try {
            await startBot(
                botId,
            );

            await loadBots();
        } catch (error) {
            setErrorMessage(
                getErrorMessage(
                    error,
                    "Nie udało się uruchomić bota.",
                ),
            );
        } finally {
            setActionBotId(
                null,
            );
        }
    }

    async function handleStopBot(
        botId: number,
    ) {
        if (
            actionBotId !== null
        ) {
            return;
        }

        setActionBotId(
            botId,
        );

        setErrorMessage(
            null,
        );

        try {
            await stopBot(
                botId,
            );

            await loadBots();
        } catch (error) {
            setErrorMessage(
                getErrorMessage(
                    error,
                    "Nie udało się zatrzymać bota.",
                ),
            );
        } finally {
            setActionBotId(
                null,
            );
        }
    }

    return (
        <section className="page">
            <header className="page-header bots-page-header">
                <div>
                    <p className="page-eyebrow">
                        Zarządzanie
                    </p>

                    <h1 className="page-title">
                        Boty
                    </h1>

                    <p className="page-description">
                        Zarządzaj botami oraz ich
                        aktualnym stanem.
                    </p>
                </div>

                <Link
                    className="primary-button"
                    to="/bots/create"
                >
                    + Utwórz bota
                </Link>
            </header>

            {errorMessage !== null && (
                <div
                    className="form-message form-message-error"
                    role="alert"
                >
                    {errorMessage}
                </div>
            )}

            <article className="content-card">
                <div className="bots-list-header">
                    <div>
                        <h2 className="content-card-title">
                            Wszystkie boty
                        </h2>

                        <p className="content-card-text">
                            Boty zapisane obecnie
                            w backendzie.
                        </p>
                    </div>

                    <div className="bots-list-header-actions">
                        <span className="dictionary-count">
                            {bots.length}
                        </span>

                        <button
                            className="secondary-button"
                            type="button"
                            disabled={
                                isLoading
                            }
                            onClick={() => {
                                void loadBots();
                            }}
                        >
                            Odśwież
                        </button>
                    </div>
                </div>

                {isLoading ? (
                    <div className="dictionary-list-state">
                        Pobieranie botów...
                    </div>
                ) : bots.length === 0 ? (
                    <div className="bots-empty-state">
                        <h3>
                            Nie masz jeszcze żadnego bota
                        </h3>

                        <p>
                            Utwórz pierwszego bota
                            i skonfiguruj konto Vinted,
                            filtry oraz strategię negocjacji.
                        </p>

                        <Link
                            className="primary-button"
                            to="/bots/create"
                        >
                            Utwórz pierwszego bota
                        </Link>
                    </div>
                ) : (
                    <div className="bots-table-wrapper">
                        <table className="bots-table">
                            <thead>
                                <tr>
                                    <th>
                                        Bot
                                    </th>

                                    <th>
                                        Konto
                                    </th>

                                    <th>
                                        Status
                                    </th>

                                    <th>
                                        ID
                                    </th>

                                    <th className="bots-actions-column">
                                        Akcje
                                    </th>
                                </tr>
                            </thead>

                            <tbody>
                                {bots.map(
                                    (bot) => {
                                        const isRunning =
                                            bot.status
                                                .toUpperCase()
                                            === "RUNNING";

                                        const isActionInProgress =
                                            actionBotId
                                            === bot.id;

                                        return (
                                            <tr
                                                key={
                                                    bot.id
                                                }
                                            >
                                                <td>
                                                    <div className="bot-name-cell">
                                                        <strong>
                                                            {bot.name}
                                                        </strong>

                                                        <span>
                                                            Vinted
                                                        </span>
                                                    </div>
                                                </td>

                                                <td>
                                                    {bot.email}
                                                </td>

                                                <td>
                                                    <BotStatus
                                                        status={
                                                            bot.status
                                                        }
                                                    />
                                                </td>

                                                <td>
                                                    <span className="bot-id">
                                                        #{bot.id}
                                                    </span>
                                                </td>

                                                <td>
                                                    <div className="bot-row-actions">
                                                        {isRunning ? (
                                                            <button
                                                                className="bot-stop-button"
                                                                type="button"
                                                                disabled={
                                                                    isActionInProgress
                                                                }
                                                                onClick={() => {
                                                                    void handleStopBot(
                                                                        bot.id,
                                                                    );
                                                                }}
                                                            >
                                                                {isActionInProgress
                                                                    ? "Zatrzymywanie..."
                                                                    : "Zatrzymaj"}
                                                            </button>
                                                        ) : (
                                                            <button
                                                                className="bot-start-button"
                                                                type="button"
                                                                disabled={
                                                                    isActionInProgress
                                                                }
                                                                onClick={() => {
                                                                    void handleStartBot(
                                                                        bot.id,
                                                                    );
                                                                }}
                                                            >
                                                                {isActionInProgress
                                                                    ? "Uruchamianie..."
                                                                    : "Uruchom"}
                                                            </button>
                                                        )}
                                                    </div>
                                                </td>
                                            </tr>
                                        );
                                    },
                                )}
                            </tbody>
                        </table>
                    </div>
                )}
            </article>
        </section>
    );
}

interface BotStatusProps {
    status: string;
}

function BotStatus({
    status,
}: BotStatusProps) {
    const normalizedStatus =
        status.toUpperCase();

    const statusClassName =
        normalizedStatus === "RUNNING"
            ? "bot-status bot-status-running"
            : "bot-status bot-status-stopped";

    return (
        <span className={statusClassName}>
            <span className="bot-status-dot" />

            {formatBotStatus(
                status,
            )}
        </span>
    );
}

function formatBotStatus(
    status: string,
): string {
    switch (
        status.toUpperCase()
    ) {
        case "RUNNING":
            return "Uruchomiony";

        case "STOPPED":
            return "Zatrzymany";

        default:
            return status;
    }
}

function getErrorMessage(
    error: unknown,
    fallbackMessage: string,
): string {
    if (
        error instanceof Error
    ) {
        return error.message;
    }

    return fallbackMessage;
}

export default BotsPage;