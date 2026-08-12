import type {
    BotDetails,
    BotListItem,
    CreateBotRequest,
} from "../types/bots";


const BOTS_BASE_URL =
    "/api/bots";


export interface BotOfferQuota {
    limit: number;
    used: number;
    remaining: number;
}


export async function getBots(): Promise<BotListItem[]> {

    const response =
        await fetch(
            BOTS_BASE_URL,
        );


    if (!response.ok) {

        throw new Error(
            `Nie udało się pobrać botów. Status HTTP: ${response.status}.`,
        );
    }


    return response.json() as Promise<BotListItem[]>;
}


export async function getBot(
    botId: number,
): Promise<BotDetails> {

    const response =
        await fetch(
            `${BOTS_BASE_URL}/${botId}`,
        );


    if (response.status === 404) {

        throw new Error(
            `Nie znaleziono bota ${botId}.`,
        );
    }


    if (!response.ok) {

        throw new Error(
            await getApiErrorMessage(
                response,
                `Nie udało się pobrać bota ${botId}. Status HTTP: ${response.status}.`,
            ),
        );
    }


    return response.json() as Promise<BotDetails>;
}


export async function getBotOfferQuota(
    botId: number,
): Promise<BotOfferQuota> {

    const response =
        await fetch(
            `${BOTS_BASE_URL}/${botId}/offer-quota`,
        );


    if (response.status === 404) {

        throw new Error(
            `Nie znaleziono bota ${botId}.`,
        );
    }


    if (!response.ok) {

        throw new Error(
            `Nie udało się pobrać dziennego limitu ofert dla bota ${botId}. Status HTTP: ${response.status}.`,
        );
    }


    return response.json() as Promise<BotOfferQuota>;
}


export async function createBot(
    request: CreateBotRequest,
): Promise<void> {

    const response =
        await fetch(
            BOTS_BASE_URL,
            {
                method: "POST",

                headers: {
                    "Content-Type":
                        "application/json",
                },

                body:
                    JSON.stringify(
                        request,
                    ),
            },
        );


    if (response.status === 400) {

        throw new Error(
            "Backend odrzucił konfigurację bota. Sprawdź wprowadzone dane.",
        );
    }


    if (response.status === 409) {

        throw new Error(
            "Nie można utworzyć bota, ponieważ wystąpił konflikt z istniejącymi danymi.",
        );
    }


    if (!response.ok) {

        throw new Error(
            `Nie udało się utworzyć bota. Status HTTP: ${response.status}.`,
        );
    }
}


export async function updateBot(
    botId: number,
    request: CreateBotRequest,
): Promise<void> {

    const response =
        await fetch(
            `${BOTS_BASE_URL}/${botId}`,
            {
                method: "PATCH",

                headers: {
                    "Content-Type":
                        "application/json",
                },

                body:
                    JSON.stringify(
                        request,
                    ),
            },
        );


    if (response.status === 400) {

        throw new Error(
            await getApiErrorMessage(
                response,
                "Backend odrzucił zmienioną konfigurację bota. Sprawdź wprowadzone dane.",
            ),
        );
    }


    if (response.status === 404) {

        throw new Error(
            `Nie znaleziono bota ${botId}.`,
        );
    }


    if (response.status === 409) {

        throw new Error(
            await getApiErrorMessage(
                response,
                "Nie można edytować tego bota w jego obecnym stanie.",
            ),
        );
    }


    if (!response.ok) {

        throw new Error(
            await getApiErrorMessage(
                response,
                `Nie udało się zapisać zmian bota. Status HTTP: ${response.status}.`,
            ),
        );
    }
}


export async function startBot(
    botId: number,
): Promise<void> {

    const response =
        await fetch(
            `${BOTS_BASE_URL}/${botId}/start`,
            {
                method: "PATCH",
            },
        );


    if (response.status === 404) {

        throw new Error(
            "Nie znaleziono bota.",
        );
    }


    if (!response.ok) {

        throw new Error(
            `Nie udało się uruchomić bota. Status HTTP: ${response.status}.`,
        );
    }
}


export async function stopBot(
    botId: number,
): Promise<void> {

    const response =
        await fetch(
            `${BOTS_BASE_URL}/${botId}/stop`,
            {
                method: "PATCH",
            },
        );


    if (response.status === 404) {

        throw new Error(
            "Nie znaleziono bota.",
        );
    }


    if (!response.ok) {

        throw new Error(
            `Nie udało się zatrzymać bota. Status HTTP: ${response.status}.`,
        );
    }
}


async function getApiErrorMessage(
    response: Response,
    fallbackMessage: string,
): Promise<string> {

    try {

        const body =
            await response.json() as {
                message?: unknown;
            };


        if (
            typeof body.message
            === "string"
            && body.message.trim().length
            > 0
        ) {

            return body.message;
        }

    } catch {

        /*
         * Odpowiedź nie musi mieć JSON-a.
         * W takim przypadku używamy komunikatu zapasowego.
         */
    }


    return fallbackMessage;
}
