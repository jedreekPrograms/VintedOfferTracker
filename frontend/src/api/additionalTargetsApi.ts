import type {
    BotAdditionalTargetDetails,
    UpsertBotAdditionalTargetRequest,
} from "../types/bots";
import {
    assertApiResponse,
    getApiErrorMessage,
} from "./apiError";

function baseUrl(botId: number): string {
    return `/api/bots/${botId}/additional-targets`;
}

export async function getAdditionalTargets(
    botId: number,
): Promise<BotAdditionalTargetDetails[]> {
    const response = await fetch(baseUrl(botId));

    await assertApiResponse(
        response,
        `Nie udało się pobrać dodatkowych produktów bota. Status HTTP: ${response.status}.`,
    );

    return response.json() as Promise<BotAdditionalTargetDetails[]>;
}

export async function createAdditionalTarget(
    botId: number,
    request: UpsertBotAdditionalTargetRequest,
): Promise<BotAdditionalTargetDetails> {
    return sendTargetRequest(botId, null, "POST", request);
}

export async function updateAdditionalTarget(
    botId: number,
    targetId: number,
    request: UpsertBotAdditionalTargetRequest,
): Promise<BotAdditionalTargetDetails> {
    return sendTargetRequest(botId, targetId, "PATCH", request);
}

export async function deactivateAdditionalTarget(
    botId: number,
    targetId: number,
): Promise<void> {
    const response = await fetch(`${baseUrl(botId)}/${targetId}`, {
        method: "DELETE",
    });

    if (response.status === 404) {
        throw new Error("Nie znaleziono dodatkowego produktu.");
    }

    if (response.status === 400 || response.status === 409) {
        throw new Error(
            await getApiErrorMessage(
                response,
                "Nie udało się wyłączyć dodatkowego produktu.",
            ),
        );
    }

    await assertApiResponse(
        response,
        `Nie udało się wyłączyć dodatkowego produktu. Status HTTP: ${response.status}.`,
    );
}

async function sendTargetRequest(
    botId: number,
    targetId: number | null,
    method: "POST" | "PATCH",
    request: UpsertBotAdditionalTargetRequest,
): Promise<BotAdditionalTargetDetails> {
    const url = targetId === null
        ? baseUrl(botId)
        : `${baseUrl(botId)}/${targetId}`;

    const response = await fetch(url, {
        method,
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(request),
    });

    if (response.status === 404) {
        throw new Error("Nie znaleziono bota lub dodatkowego produktu.");
    }

    if (response.status === 400 || response.status === 409) {
        throw new Error(
            await getApiErrorMessage(
                response,
                "Backend odrzucił konfigurację dodatkowego produktu.",
            ),
        );
    }

    await assertApiResponse(
        response,
        `Nie udało się zapisać dodatkowego produktu. Status HTTP: ${response.status}.`,
    );

    return response.json() as Promise<BotAdditionalTargetDetails>;
}
