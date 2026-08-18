export interface BotCredentialsResponse {
    email: string;
    password: string;
}

export async function getActionRequiredCredentials(
    botId: number,
    listingId: number,
): Promise<BotCredentialsResponse> {

    const response =
        await fetch(
            `/api/bots/${botId}/listings/${listingId}/credentials`,
        );

    if (!response.ok) {

        const responseText =
            await response.text();

        throw new Error(
            responseText.length > 0
                ? responseText
                : `Nie udało się pobrać danych logowania. HTTP ${response.status}`,
        );
    }

    return response.json();
}