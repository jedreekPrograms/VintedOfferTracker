interface ApiErrorResponse {
    message?: string;
}


export async function getApiErrorMessage(
    response: Response,
    fallbackMessage: string,
): Promise<string> {

    const responseText =
        await response.text();


    if (
        responseText.trim().length === 0
    ) {

        return fallbackMessage;
    }


    try {

        const errorResponse =
            JSON.parse(
                responseText,
            ) as ApiErrorResponse;


        if (
            typeof errorResponse.message === "string"
            && errorResponse.message.trim().length > 0
        ) {

            return errorResponse.message;
        }

    } catch {

        // Odpowiedź nie była JSON-em.
    }


    return fallbackMessage;
}