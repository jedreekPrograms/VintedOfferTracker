export interface BotCommandResponse {
    id: number;
    botId: number;
    listingId: number;
    type: "OPEN_CONVERSATION";
    status:
        | "PENDING"
        | "PROCESSING"
        | "COMPLETED"
        | "FAILED";
    createdAt: string;
}

export async function openConversationInBotSession(
    botId: number,
    listingId: number,
): Promise<BotCommandResponse> {

    const response =
        await fetch(
            `/api/bots/${botId}/commands/listings/${listingId}/open-conversation`,
            {
                method: "POST",
            },
        );

    if (!response.ok) {

        const responseText =
            await response.text();

        throw new Error(
            responseText.length > 0
                ? responseText
                : `Nie udało się wysłać polecenia do bota. HTTP ${response.status}`,
        );
    }

    return response.json();
}