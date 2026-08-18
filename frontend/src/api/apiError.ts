interface ApiErrorResponse {
    message?: unknown;
}

export async function getApiErrorMessage(
    response: Response,
    fallbackMessage: string,
): Promise<string> {
    const responseText = await response.text();

    if (responseText.trim().length === 0) {
        return fallbackMessage;
    }

    try {
        const errorResponse = JSON.parse(responseText) as ApiErrorResponse;

        if (
            typeof errorResponse.message === "string"
            && errorResponse.message.trim().length > 0
        ) {
            return errorResponse.message;
        }
    } catch {
        // The backend response does not have to be JSON.
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
