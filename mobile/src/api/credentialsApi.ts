import {
    assertApiResponse,
} from "./apiError";

export interface BotCredentialsResponse {
    email: string;
    password: string;
}

export async function getActionRequiredCredentials(
    botId: number,
    listingId: number,
): Promise<BotCredentialsResponse> {
    const response = await fetch(
        `/api/bots/${botId}/listings/${listingId}/credentials`,
    );

    await assertApiResponse(
        response,
        `Nie udało się pobrać danych logowania. HTTP ${response.status}`,
    );

    return response.json() as Promise<BotCredentialsResponse>;
}
