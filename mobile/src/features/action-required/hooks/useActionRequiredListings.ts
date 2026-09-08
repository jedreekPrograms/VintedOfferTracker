import {
    useCallback,
    useEffect,
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

    const loadListings =
        useCallback(
            async () => {
                setIsLoading(
                    true,
                );

                setErrorMessage(
                    null,
                );

                try {
                    const bots =
                        await getBots();

                    console.log(
                        "[ACTION REQUIRED] bots:",
                        bots,
                    );    

                    const listingsPerBot =
                        await Promise.all(
                            bots.map(
                                async (bot) => {
                                    const botListings =
                                        await getActionRequiredListings(
                                            bot.id,
                                        );

                                    console.log(
                                        `[ACTION REQUIRED] bot ${bot.id}:`,
                                        botListings,
                                    );    

                                    return botListings.map(
                                        (listing) => ({
                                            botId:
                                                bot.id,

                                            botName:
                                                bot.name,

                                            listing,
                                        }),
                                    );
                                },
                            ),
                        );

                    setListings(
                        listingsPerBot.flat(),
                    );
                } catch (error) {
                    setListings([]);

                    setErrorMessage(
                        getErrorMessage(
                            error,
                            "Nie udało się pobrać ofert do kupienia.",
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
        void loadListings();
    }, [
        loadListings,
    ]);

    return {
        listings,

        isLoading,

        errorMessage,

        reload:
            loadListings,
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