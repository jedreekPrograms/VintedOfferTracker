import {
    useState,
} from "react";

import {
    getActionRequiredCredentials,
    type BotCredentialsResponse,
} from "../api/credentialsApi";

import {
    markListingAsPurchased,
    skipListingByUser,
} from "../api/listingsApi";

import {
    useActionRequiredListings,
} from "../features/action-required/hooks/useActionRequiredListings";


function ActionRequiredPage() {

    const {
        listings,
        isLoading,
        errorMessage,
        reload,
    } = useActionRequiredListings();


    const [
        credentialsByListingId,
        setCredentialsByListingId,
    ] = useState<Record<number, BotCredentialsResponse>>(
        {},
    );


    const [
        loadingCredentialsListingId,
        setLoadingCredentialsListingId,
    ] = useState<number | null>(
        null,
    );


    const [
        visiblePasswordListingId,
        setVisiblePasswordListingId,
    ] = useState<number | null>(
        null,
    );


    const [
        processingListingId,
        setProcessingListingId,
    ] = useState<number | null>(
        null,
    );


    const [
        actionError,
        setActionError,
    ] = useState<string | null>(
        null,
    );


    const [
        infoMessage,
        setInfoMessage,
    ] = useState<string | null>(
        null,
    );


    async function handleLoadCredentials(
        botId: number,
        listingId: number,
    ) {

        setLoadingCredentialsListingId(
            listingId,
        );

        setActionError(
            null,
        );

        setInfoMessage(
            null,
        );

        try {

            const credentials =
                await getActionRequiredCredentials(
                    botId,
                    listingId,
                );

            setCredentialsByListingId(
                (current) => ({
                    ...current,
                    [listingId]:
                        credentials,
                }),
            );

        } catch (error) {

            setActionError(
                error instanceof Error
                    ? error.message
                    : "Nie udało się pobrać danych logowania.",
            );

        } finally {

            setLoadingCredentialsListingId(
                null,
            );
        }
    }


    async function handleCopy(
        value: string,
        label: string,
    ) {

        try {

            await navigator.clipboard.writeText(
                value,
            );

            setInfoMessage(
                `${label} skopiowano do schowka.`,
            );

        } catch {

            setInfoMessage(
                `Nie udało się skopiować: ${label}.`,
            );
        }
    }


    async function handlePurchased(
        botId: number,
        listingId: number,
        title: string,
    ) {

        const confirmed =
            window.confirm(
                `Czy na pewno kupiłeś "${title}"?\n\n`
                + "Oferta zostanie zapisana jako zakupiona "
                + "i będzie uwzględniana w statystykach.",
            );

        if (!confirmed) {
            return;
        }

        setProcessingListingId(
            listingId,
        );

        setActionError(
            null,
        );

        setInfoMessage(
            null,
        );

        try {

            await markListingAsPurchased(
                botId,
                listingId,
            );

            setInfoMessage(
                `Oferta "${title}" została zapisana jako kupiona.`,
            );

            removeCredentialsFromMemory(
                listingId,
            );

            await reload();

        } catch (error) {

            setActionError(
                error instanceof Error
                    ? error.message
                    : "Nie udało się oznaczyć oferty jako kupione.",
            );

        } finally {

            setProcessingListingId(
                null,
            );
        }
    }


    async function handleSkip(
        botId: number,
        listingId: number,
        title: string,
    ) {

        const confirmed =
            window.confirm(
                `Czy na pewno chcesz odrzucić "${title}"?\n\n`
                + "Oferta zniknie z listy ofert do kupienia "
                + "i zostanie zapisana jako odrzucona przez Ciebie.",
            );

        if (!confirmed) {
            return;
        }

        setProcessingListingId(
            listingId,
        );

        setActionError(
            null,
        );

        setInfoMessage(
            null,
        );

        try {

            await skipListingByUser(
                botId,
                listingId,
            );

            setInfoMessage(
                `Oferta "${title}" została odrzucona.`,
            );

            removeCredentialsFromMemory(
                listingId,
            );

            await reload();

        } catch (error) {

            setActionError(
                error instanceof Error
                    ? error.message
                    : "Nie udało się odrzucić oferty.",
            );

        } finally {

            setProcessingListingId(
                null,
            );
        }
    }


    function removeCredentialsFromMemory(
        listingId: number,
    ) {

        setCredentialsByListingId(
            (current) => {

                const updated = {
                    ...current,
                };

                delete updated[
                    listingId
                ];

                return updated;
            },
        );

        if (
            visiblePasswordListingId
            === listingId
        ) {

            setVisiblePasswordListingId(
                null,
            );
        }
    }


    return (

        <section className="page">

            <header
                className="
                    page-header
                    action-required-page-header
                "
            >

                <div>

                    <p className="page-eyebrow">
                        Zakup manualny
                    </p>

                    <h1 className="page-title">
                        Oferty do kupienia
                    </h1>

                    <p className="page-description">

                        Oferty, dla których bot
                        wynegocjował akceptowalną cenę.

                        {" "}

                        Po sprawdzeniu oferty oznacz ją
                        jako kupioną albo odrzuć.

                    </p>

                </div>


                <button
                    className="secondary-button"
                    type="button"
                    disabled={isLoading}
                    onClick={() => {
                        void reload();
                    }}
                >

                    {isLoading
                        ? "Odświeżanie..."
                        : "Odśwież"}

                </button>

            </header>


            {errorMessage !== null && (

                <div
                    className="
                        form-message
                        form-message-error
                    "
                    role="alert"
                >
                    {errorMessage}
                </div>

            )}


            {actionError !== null && (

                <div
                    className="
                        form-message
                        form-message-error
                    "
                    role="alert"
                >
                    {actionError}
                </div>

            )}


            {infoMessage !== null && (

                <div
                    className="form-message"
                    role="status"
                >
                    {infoMessage}
                </div>

            )}


            {isLoading ? (

                <article className="content-card">

                    <div className="dictionary-list-state">
                        Pobieranie ofert...
                    </div>

                </article>

            ) : listings.length === 0 ? (

                <article className="content-card">

                    <div className="action-required-empty">

                        <div className="action-required-empty-icon">
                            ✓
                        </div>

                        <h2>
                            Wszystko przejrzane
                        </h2>

                        <p>
                            Aktualnie nie ma ofert
                            wymagających Twojej decyzji.
                        </p>

                    </div>

                </article>

            ) : (

                <div className="action-required-grid">

                    {listings.map(
                        ({
                            botId,
                            botName,
                            listing,
                        }) => {


                            const credentials =
                                credentialsByListingId[
                                    listing.id
                                ];


                            const isLoadingCredentials =
                                loadingCredentialsListingId
                                === listing.id;


                            const isPasswordVisible =
                                visiblePasswordListingId
                                === listing.id;


                            const isProcessing =
                                processingListingId
                                === listing.id;


                            const listingUrl =
                                getAbsoluteVintedUrl(
                                    listing.url,
                                );


                            const savings =
                                calculateSavings(
                                    listing.originalPrice,
                                    listing.currentPrice,
                                );


                            const discountPercentage =
                                calculateDiscountPercentage(
                                    listing.originalPrice,
                                    listing.currentPrice,
                                );


                            return (

                                <article
                                    key={listing.id}
                                    className="action-required-card"
                                >


                                    <div className="action-required-card-header">

                                        <div>

                                            <span className="action-required-badge">
                                                Do decyzji
                                            </span>

                                            <h2>
                                                {listing.title}
                                            </h2>

                                        </div>


                                        <span className="action-required-bot">
                                            {botName}
                                        </span>

                                    </div>


                                    <div className="action-required-prices">

                                        <div>

                                            <span>
                                                Cena początkowa
                                            </span>

                                            <strong className="original-price">
                                                {formatPrice(
                                                    listing.originalPrice,
                                                )}
                                            </strong>

                                        </div>


                                        <div>

                                            <span>
                                                Wynegocjowana cena
                                            </span>

                                            <strong className="negotiated-price">
                                                {formatPrice(
                                                    listing.currentPrice,
                                                )}
                                            </strong>

                                        </div>

                                    </div>


                                    <div className="action-required-savings">

                                        <div>

                                            <span>
                                                Wynegocjowano
                                            </span>

                                            <strong>
                                                {formatPrice(
                                                    savings,
                                                )}
                                            </strong>

                                        </div>


                                        <div className="action-required-discount">

                                            -
                                            {discountPercentage.toFixed(
                                                1,
                                            )}
                                            %

                                        </div>

                                    </div>


                                    <dl className="action-required-details">

                                        <div>

                                            <dt>
                                                Bot
                                            </dt>

                                            <dd>
                                                {botName}
                                                {" "}

                                                <span>
                                                    #{botId}
                                                </span>
                                            </dd>

                                        </div>


                                        <div>

                                            <dt>
                                                Listing ID
                                            </dt>

                                            <dd>
                                                {listing.listingId}
                                            </dd>

                                        </div>


                                        <div>

                                            <dt>
                                                Krok negocjacji
                                            </dt>

                                            <dd>
                                                {listing.currentStep}
                                            </dd>

                                        </div>


                                        <div>

                                            <dt>
                                                Status
                                            </dt>

                                            <dd>
                                                {listing.status}
                                            </dd>

                                        </div>

                                    </dl>


                                    <div className="action-required-login">

                                        <h3>
                                            Konto Vinted
                                        </h3>


                                        {credentials === undefined ? (

                                            <button
                                                className="secondary-button"
                                                type="button"
                                                disabled={
                                                    isLoadingCredentials
                                                    || isProcessing
                                                }
                                                onClick={() => {

                                                    void handleLoadCredentials(
                                                        botId,
                                                        listing.id,
                                                    );

                                                }}
                                            >

                                                {isLoadingCredentials
                                                    ? "Pobieranie..."
                                                    : "Pokaż dane logowania"}

                                            </button>

                                        ) : (

                                            <div className="action-required-credentials">

                                                <div>

                                                    <span>
                                                        Login
                                                    </span>

                                                    <strong>
                                                        {credentials.email}
                                                    </strong>

                                                    <button
                                                        className="secondary-button"
                                                        type="button"
                                                        onClick={() => {

                                                            void handleCopy(
                                                                credentials.email,
                                                                "Login",
                                                            );

                                                        }}
                                                    >
                                                        Kopiuj
                                                    </button>

                                                </div>


                                                <div>

                                                    <span>
                                                        Hasło
                                                    </span>

                                                    <strong>

                                                        {isPasswordVisible
                                                            ? credentials.password
                                                            : "••••••••••••"}

                                                    </strong>


                                                    <button
                                                        className="secondary-button"
                                                        type="button"
                                                        onClick={() => {

                                                            setVisiblePasswordListingId(
                                                                isPasswordVisible
                                                                    ? null
                                                                    : listing.id,
                                                            );

                                                        }}
                                                    >

                                                        {isPasswordVisible
                                                            ? "Ukryj"
                                                            : "Pokaż"}

                                                    </button>


                                                    <button
                                                        className="secondary-button"
                                                        type="button"
                                                        onClick={() => {

                                                            void handleCopy(
                                                                credentials.password,
                                                                "Hasło",
                                                            );

                                                        }}
                                                    >
                                                        Kopiuj
                                                    </button>

                                                </div>

                                            </div>

                                        )}

                                    </div>


                                    <div className="action-required-actions">

                                        <a
                                            className="primary-button"
                                            href={listingUrl}
                                            target="_blank"
                                            rel="noreferrer"
                                        >
                                            Otwórz na Vinted
                                        </a>


                                        <button
                                            className="purchase-finished-button"
                                            type="button"
                                            disabled={isProcessing}
                                            onClick={() => {

                                                void handlePurchased(
                                                    botId,
                                                    listing.id,
                                                    listing.title,
                                                );

                                            }}
                                        >

                                            {isProcessing
                                                ? "Zapisywanie..."
                                                : "✓ Kupiłem"}

                                        </button>


                                        <button
                                            className="action-required-reject-button"
                                            type="button"
                                            disabled={isProcessing}
                                            onClick={() => {

                                                void handleSkip(
                                                    botId,
                                                    listing.id,
                                                    listing.title,
                                                );

                                            }}
                                        >

                                            ✕ Odrzuć

                                        </button>

                                    </div>

                                </article>
                            );
                        },
                    )}

                </div>
            )}

        </section>
    );
}


function getAbsoluteVintedUrl(
    url: string,
): string {

    if (
        url.startsWith(
            "http://",
        )
        || url.startsWith(
            "https://",
        )
    ) {

        return url;
    }


    if (
        url.startsWith(
            "/",
        )
    ) {

        return `https://www.vinted.pl${url}`;
    }


    return `https://www.vinted.pl/${url}`;
}


function calculateSavings(
    originalPrice: number,
    currentPrice: number,
): number {

    return Math.max(
        0,
        originalPrice - currentPrice,
    );
}


function calculateDiscountPercentage(
    originalPrice: number,
    currentPrice: number,
): number {

    if (
        originalPrice <= 0
    ) {

        return 0;
    }


    return Math.max(
        0,
        (
            (
                originalPrice
                - currentPrice
            )
            / originalPrice
        )
        * 100,
    );
}


function formatPrice(
    price: number,
): string {

    return new Intl.NumberFormat(
        "pl-PL",
        {
            style: "currency",
            currency: "PLN",
        },
    ).format(
        price,
    );
}


export default ActionRequiredPage;