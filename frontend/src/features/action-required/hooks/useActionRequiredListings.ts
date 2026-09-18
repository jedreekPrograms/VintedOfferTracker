import {
    useCallback,
    useEffect,
    useRef,
    useState,
} from "react";

import {
    getBots,
} from "../../../api/botsApi";

import {
    getActionRequiredListings,
} from "../../../api/listingsApi";

import type {
    ActionRequiredListing,
} from "../../../types/listings";

const ACTION_REQUIRED_POLL_INTERVAL_MS = 15_000;
const MAX_CONCURRENT_BOT_REQUESTS = 3;

interface UseActionRequiredListingsResult {
    listings: ActionRequiredListing[];

    isLoading: boolean;

    errorMessage: string | null;

    reload: () => Promise<void>;
}

export function useActionRequiredListings():
    UseActionRequiredListingsResult {
    const [
        listings,
        setListings,
    ] = useState<ActionRequiredListing[]>([]);

    const [
        isLoading,
        setIsLoading,
    ] = useState(true);

    const [
        errorMessage,
        setErrorMessage,
    ] = useState<string | null>(
        null,
    );

    const inFlightRef =
        useRef<Promise<void> | null>(
            null,
        );

    const loadListings =
        useCallback(
            async (
                background = false,
            ) => {
                if (
                    inFlightRef.current !== null
                ) {
                    await inFlightRef.current;
                    return;
                }

                const request =
                    (async () => {
                        if (
                            !background
                        ) {
                            setIsLoading(
                                true,
                            );

                            setErrorMessage(
                                null,
                            );
                        }

                        try {
                            const bots =
                                await getBots();

                            const listingsPerBot:
                                ActionRequiredListing[][] =
                                new Array(
                                    bots.length,
                                );

                            let nextBotIndex =
                                0;

                            async function worker() {
                                while (true) {
                                    const currentIndex =
                                        nextBotIndex++;

                                    if (
                                        currentIndex
                                        >= bots.length
                                    ) {
                                        return;
                                    }

                                    const bot =
                                        bots[
                                            currentIndex
                                        ];

                                    const botListings =
                                        await getActionRequiredListings(
                                            bot.id,
                                        );

                                    listingsPerBot[
                                        currentIndex
                                    ] =
                                        botListings.map(
                                            (listing) => ({
                                                botId:
                                                    bot.id,

                                                botName:
                                                    bot.name,

                                                listing,
                                            }),
                                        );
                                }
                            }

                            const workerCount =
                                Math.min(
                                    MAX_CONCURRENT_BOT_REQUESTS,
                                    bots.length,
                                );

                            await Promise.all(
                                Array.from(
                                    {
                                        length:
                                            workerCount,
                                    },
                                    () =>
                                        worker(),
                                ),
                            );

                            setListings(
                                listingsPerBot.flat(),
                            );

                            setErrorMessage(
                                null,
                            );
                        } catch (error) {
                            if (
                                !background
                            ) {
                                setListings(
                                    [],
                                );

                                setErrorMessage(
                                    getErrorMessage(
                                        error,
                                        "Nie udało się pobrać ofert do kupienia.",
                                    ),
                                );
                            }
                        } finally {
                            if (
                                !background
                            ) {
                                setIsLoading(
                                    false,
                                );
                            }
                        }
                    })();

                inFlightRef.current =
                    request;

                try {
                    await request;
                } finally {
                    if (
                        inFlightRef.current
                        === request
                    ) {
                        inFlightRef.current =
                            null;
                    }
                }
            },
            [],
        );

    const reload =
        useCallback(
            async () => {
                await loadListings(
                    false,
                );
            },
            [
                loadListings,
            ],
        );

    useEffect(() => {
        void reload();
    }, [
        reload,
    ]);

    useEffect(
        () => {
            const refreshInBackground =
                () => {
                    if (
                        document.hidden
                    ) {
                        return;
                    }

                    void loadListings(
                        true,
                    );
                };

            const intervalId =
                window.setInterval(
                    refreshInBackground,
                    ACTION_REQUIRED_POLL_INTERVAL_MS,
                );

            const handleVisibilityChange =
                () => {
                    if (
                        !document.hidden
                    ) {
                        refreshInBackground();
                    }
                };

            document.addEventListener(
                "visibilitychange",
                handleVisibilityChange,
            );

            return () => {
                window.clearInterval(
                    intervalId,
                );

                document.removeEventListener(
                    "visibilitychange",
                    handleVisibilityChange,
                );
            };
        },
        [
            loadListings,
        ],
    );

    return {
        listings,

        isLoading,

        errorMessage,

        reload,
    };
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
