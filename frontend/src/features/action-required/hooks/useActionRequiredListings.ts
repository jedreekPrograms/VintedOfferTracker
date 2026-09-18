import {
    useCallback,
    useEffect,
    useRef,
    useState,
} from "react";

import {
    getAllActionRequiredListings,
} from "../../../api/listingsApi";

import type {
    ActionRequiredListing,
} from "../../../types/listings";

const ACTION_REQUIRED_POLL_INTERVAL_MS = 15_000;

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
    ] = useState<string | null>(null);

    const inFlightRef =
        useRef<Promise<void> | null>(null);

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
                        if (!background) {
                            setIsLoading(true);
                            setErrorMessage(null);
                        }

                        try {
                            setListings(
                                await getAllActionRequiredListings(),
                            );
                            setErrorMessage(null);
                        } catch (error) {
                            if (!background) {
                                setListings([]);
                                setErrorMessage(
                                    getErrorMessage(
                                        error,
                                        "Nie udało się pobrać ofert do kupienia.",
                                    ),
                                );
                            }
                        } finally {
                            if (!background) {
                                setIsLoading(false);
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
                await loadListings(false);
            },
            [
                loadListings,
            ],
        );

    useEffect(
        () => {
            void reload();
        },
        [
            reload,
        ],
    );

    useEffect(
        () => {
            const refreshInBackground =
                () => {
                    if (document.hidden) {
                        return;
                    }

                    void loadListings(true);
                };

            const intervalId =
                window.setInterval(
                    refreshInBackground,
                    ACTION_REQUIRED_POLL_INTERVAL_MS,
                );

            const handleVisibilityChange =
                () => {
                    if (!document.hidden) {
                        refreshInBackground();
                    }
                };

            document.addEventListener(
                "visibilitychange",
                handleVisibilityChange,
            );

            return () => {
                window.clearInterval(intervalId);
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
    return error instanceof Error
        ? error.message
        : fallbackMessage;
}
