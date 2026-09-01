import type {
    BotDetails,
    BotEditCapabilities,
    BotListItem,
    CreateBotRequest,
} from "../types/bots";
import {
    assertApiResponse,
    getApiErrorMessage,
} from "./apiError";

const BOTS_BASE_URL = "/api/bots";

export interface BotOfferQuota {
    limit: number;
    used: number;
    remaining: number;
}

export interface BotRuntimeState {
    botId: number;
    runtimeStatus: "IDLE" | "QUEUED" | "WORKING" | "COOLDOWN" | "ERROR";
    lastRunStartedAt: string | null;
    lastRunFinishedAt: string | null;
    nextRunAt: string | null;
    lastRunDurationMs: number | null;
    consecutiveFailures: number;
    lastError: string | null;
    workerSlot: number | null;
    sessionBlockedSince: string | null;
    sessionBlockCount: number;
    updatedAt: string | null;
}

export async function getBots(): Promise<BotListItem[]> {
    const response = await fetch(BOTS_BASE_URL);

    await assertApiResponse(
        response,
        `Nie udało się pobrać botów. Status HTTP: ${response.status}.`,
    );

    return response.json() as Promise<BotListItem[]>;
}

export async function getBot(botId: number): Promise<BotDetails> {
    const response = await fetch(`${BOTS_BASE_URL}/${botId}`);

    if (response.status === 404) {
        throw new Error(`Nie znaleziono bota ${botId}.`);
    }

    await assertApiResponse(
        response,
        `Nie udało się pobrać bota ${botId}. Status HTTP: ${response.status}.`,
    );

    return response.json() as Promise<BotDetails>;
}

export async function getBotEditCapabilities(
    botId: number,
): Promise<BotEditCapabilities> {
    const response = await fetch(`${BOTS_BASE_URL}/${botId}/edit-capabilities`);

    if (response.status === 404) {
        throw new Error(`Nie znaleziono bota ${botId}.`);
    }

    await assertApiResponse(
        response,
        `Nie udało się pobrać zasad edycji bota ${botId}. Status HTTP: ${response.status}.`,
    );

    return response.json() as Promise<BotEditCapabilities>;
}

export async function getBotOfferQuota(
    botId: number,
): Promise<BotOfferQuota> {
    const response = await fetch(`${BOTS_BASE_URL}/${botId}/offer-quota`);

    if (response.status === 404) {
        throw new Error(`Nie znaleziono bota ${botId}.`);
    }

    await assertApiResponse(
        response,
        `Nie udało się pobrać dziennego limitu ofert dla bota ${botId}. Status HTTP: ${response.status}.`,
    );

    return response.json() as Promise<BotOfferQuota>;
}

export async function getBotRuntimeState(
    botId: number,
): Promise<BotRuntimeState> {
    const response = await fetch(`${BOTS_BASE_URL}/${botId}/runtime`);

    if (response.status === 404) {
        throw new Error(`Nie znaleziono stanu runtime bota ${botId}.`);
    }

    await assertApiResponse(
        response,
        `Nie udało się pobrać stanu runtime bota ${botId}. Status HTTP: ${response.status}.`,
    );

    return response.json() as Promise<BotRuntimeState>;
}

export async function createBot(request: CreateBotRequest): Promise<void> {
    const response = await fetch(BOTS_BASE_URL, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(request),
    });

    if (response.status === 400) {
        throw new Error("Backend odrzucił konfigurację bota. Sprawdź wprowadzone dane.");
    }

    if (response.status === 409) {
        throw new Error("Nie można utworzyć bota, ponieważ wystąpił konflikt z istniejącymi danymi.");
    }

    await assertApiResponse(
        response,
        `Nie udało się utworzyć bota. Status HTTP: ${response.status}.`,
    );
}

export async function updateBot(
    botId: number,
    request: CreateBotRequest,
): Promise<void> {
    const response = await fetch(`${BOTS_BASE_URL}/${botId}`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(request),
    });

    if (response.status === 400) {
        throw new Error(
            await getApiErrorMessage(
                response,
                "Backend odrzucił zmienioną konfigurację bota. Sprawdź wprowadzone dane.",
            ),
        );
    }

    if (response.status === 404) {
        throw new Error(`Nie znaleziono bota ${botId}.`);
    }

    if (response.status === 409) {
        throw new Error(
            await getApiErrorMessage(
                response,
                "Nie można edytować tego bota w jego obecnym stanie.",
            ),
        );
    }

    await assertApiResponse(
        response,
        `Nie udało się zapisać zmian bota. Status HTTP: ${response.status}.`,
    );
}

export async function deleteBot(botId: number): Promise<void> {
    const response = await fetch(`${BOTS_BASE_URL}/${botId}`, {
        method: "DELETE",
    });

    if (response.status === 404) {
        throw new Error(`Nie znaleziono bota ${botId}.`);
    }

    if (response.status === 409) {
        throw new Error(
            await getApiErrorMessage(
                response,
                "Nie można usunąć tego bota. Najpierw zatrzymaj go i zakończ aktywne negocjacje.",
            ),
        );
    }

    await assertApiResponse(
        response,
        `Nie udało się usunąć bota. Status HTTP: ${response.status}.`,
    );
}

export async function startBot(botId: number): Promise<void> {
    const response = await fetch(`${BOTS_BASE_URL}/${botId}/start`, {
        method: "PATCH",
    });

    if (response.status === 404) {
        throw new Error("Nie znaleziono bota.");
    }

    await assertApiResponse(
        response,
        `Nie udało się uruchomić bota. Status HTTP: ${response.status}.`,
    );
}

export async function stopBot(botId: number): Promise<void> {
    const response = await fetch(`${BOTS_BASE_URL}/${botId}/stop`, {
        method: "PATCH",
    });

    if (response.status === 404) {
        throw new Error("Nie znaleziono bota.");
    }

    await assertApiResponse(
        response,
        `Nie udało się zatrzymać bota. Status HTTP: ${response.status}.`,
    );
}
