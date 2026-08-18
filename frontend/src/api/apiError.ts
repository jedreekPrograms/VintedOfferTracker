interface ApiErrorResponse {
    message?: unknown;
}

export async function getApiErrorMessage(
    response: Response,
    fallbackMessage: string,
): Promise<string> {
    const responseText = await response.text();
    const trimmedResponse = responseText.trim();

    if (trimmedResponse.length === 0) {
        return fallbackMessage;
    }

    try {
        const errorResponse = JSON.parse(trimmedResponse) as ApiErrorResponse;

        if (
            typeof errorResponse.message === "string"
            && errorResponse.message.trim().length > 0
        ) {
            return errorResponse.message.trim();
        }
    } catch {
        const contentType = response.headers
            .get("content-type")
            ?.toLowerCase() ?? "";

        if (contentType.includes("text/plain")) {
            return trimmedResponse;
        }
    }

    return fallbackMessage;
}

export async function assertApiResponse(
    response: Response,
    fallbackMessage: string,
): Promise<void> {
    if (response.ok) {
        return;
    }

    throw new Error(
        await getApiErrorMessage(response, fallbackMessage),
    );
}
