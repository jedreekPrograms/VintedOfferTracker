import {
    useEffect,
    useState,
} from "react";

import {
    useNavigate,
    useParams,
} from "react-router-dom";

import {
    deleteBot,
    getBot,
} from "../../../api/botsApi";

import type {
    BotDetails,
} from "../../../types/bots";


function DeleteBotPanel() {

    const navigate =
        useNavigate();


    const {
        botId:
            botIdParam,
    } = useParams<{
        botId: string;
    }>();


    const botId =
        Number(
            botIdParam,
        );


    const isBotIdValid =
        Number.isInteger(
            botId,
        )
        && botId > 0;


    const [
        bot,
        setBot,
    ] = useState<BotDetails | null>(
        null,
    );


    const [
        isLoading,
        setIsLoading,
    ] = useState(
        true,
    );


    const [
        isDeleting,
        setIsDeleting,
    ] = useState(
        false,
    );


    const [
        errorMessage,
        setErrorMessage,
    ] = useState<string | null>(
        null,
    );


    useEffect(
        () => {

            if (
                !isBotIdValid
            ) {

                setIsLoading(
                    false,
                );

                return;
            }


            let cancelled =
                false;


            async function loadBot() {

                try {

                    const loadedBot =
                        await getBot(
                            botId,
                        );


                    if (
                        !cancelled
                    ) {

                        setBot(
                            loadedBot,
                        );
                    }

                } catch (error) {

                    if (
                        !cancelled
                    ) {

                        setErrorMessage(
                            getErrorMessage(
                                error,
                                "Nie udało się sprawdzić, czy bot może zostać usunięty.",
                            ),
                        );
                    }

                } finally {

                    if (
                        !cancelled
                    ) {

                        setIsLoading(
                            false,
                        );
                    }
                }
            }


            void loadBot();


            return () => {

                cancelled =
                    true;
            };

        },
        [
            botId,
            isBotIdValid,
        ],
    );


    const isStopped =
        bot !== null
        && bot.status
            .toUpperCase()
        === "STOPPED";


    async function handleDeleteBot() {

        if (
            !isBotIdValid
            || bot === null
            || !isStopped
            || isDeleting
        ) {

            return;
        }


        const confirmed =
            window.confirm(
                `Czy na pewno chcesz usunąć bota „${bot.name}” (#${bot.id})?\n\nTa operacja usunie również jego zapisane listingi, konfigurację i historię quota. Nie można jej cofnąć.`,
            );


        if (
            !confirmed
        ) {

            return;
        }


        setIsDeleting(
            true,
        );

        setErrorMessage(
            null,
        );


        try {

            await deleteBot(
                botId,
            );


            navigate(
                "/bots",
                {
                    replace: true,
                },
            );

        } catch (error) {

            setErrorMessage(
                getErrorMessage(
                    error,
                    "Nie udało się usunąć bota.",
                ),
            );

        } finally {

            setIsDeleting(
                false,
            );
        }
    }


    return (
        <section
            className="page"
            style={{
                marginTop: "24px",
            }}
        >

            <article className="content-card">

                <h2 className="content-card-title">
                    Usuń bota
                </h2>


                <p className="content-card-text">
                    Usunięcie jest trwałe. Backend pozwoli je wykonać
                    tylko wtedy, gdy bot jest zatrzymany i nie ma
                    żadnych aktywnych negocjacji ani ofert wymagających
                    Twojej reakcji.
                </p>


                {
                    !isLoading
                    && bot !== null
                    && !isStopped
                    && (
                        <div
                            className="form-message form-message-error"
                            role="alert"
                        >
                            Najpierw zatrzymaj tego bota, aby móc go usunąć.
                        </div>
                    )
                }


                {
                    errorMessage !== null
                    && (
                        <div
                            className="form-message form-message-error"
                            role="alert"
                        >
                            {errorMessage}
                        </div>
                    )
                }


                <div className="bot-form-actions">

                    <button
                        className="bot-stop-button"
                        type="button"
                        disabled={
                            isLoading
                            || isDeleting
                            || !isStopped
                        }
                        onClick={
                            () => {
                                void handleDeleteBot();
                            }
                        }
                    >
                        {
                            isDeleting
                                ? "Usuwanie..."
                                : "Usuń bota"
                        }
                    </button>

                </div>

            </article>

        </section>
    );
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


export default DeleteBotPanel;
