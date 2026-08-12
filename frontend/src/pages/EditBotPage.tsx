import {
    type FormEvent,
    useEffect,
    useState,
} from "react";

import {
    Link,
    useNavigate,
    useParams,
} from "react-router-dom";

import {
    getBot,
    updateBot,
} from "../api/botsApi";

import BasicInfoSection
    from "../features/bots/create/BasicInfoSection";

import BotFiltersSection
    from "../features/bots/create/BotFiltersSection";

import NegotiationBudgetSection
    from "../features/bots/create/NegotiationBudgetSection";

import NegotiationStepsSection
    from "../features/bots/create/NegotiationStepsSection";

import OfferStrategySection
    from "../features/bots/create/OfferStrategySection";

import VintedAccountSection
    from "../features/bots/create/VintedAccountSection";

import type {
    CreateBotFormValues,
    NegotiationStepField,
} from "../features/bots/create/botForm";

import type {
    BotDetails,
    TargetMode,
} from "../types/bots";

import {
    useBotDictionaries,
} from "../features/bots/create/hooks/useBotDictionaries";

import {
    useCreateBotForm,
} from "../features/bots/create/hooks/useCreateBotForm";

import {
    buildCreateBotRequest,
} from "../features/bots/create/mappers/buildCreateBotRequest";

import {
    validateCreateBotForm,
} from "../features/bots/create/validation/validateCreateBotForm";


function EditBotPage() {

    const navigate =
        useNavigate();


    const {
        botId:
            botIdParam,
    } =
        useParams<{
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


    const {
        form,

        setBotName,
        setEmail,
        setPassword,

        setCategory,
        setBrand,
        setTargetMode,
        setModel,
        setSearchQuery,

        setMinPrice,
        setMaxPrice,

        setAutoRaiseOfferToVintedMinimum,
        setMaxAutomaticOffer,

        setDailyNegotiationBudget,

        addNegotiationStep,
        removeNegotiationStep,
        updateNegotiationStep,

        replaceForm,
    } = useCreateBotForm();


    const [
        bot,
        setBot,
    ] = useState<BotDetails | null>(
        null,
    );


    const [
        isLoadingBot,
        setIsLoadingBot,
    ] = useState(
        true,
    );


    const [
        isBaseFormInitialized,
        setIsBaseFormInitialized,
    ] = useState(
        false,
    );


    const [
        isInitialModelResolved,
        setIsInitialModelResolved,
    ] = useState(
        false,
    );


    const [
        initializationError,
        setInitializationError,
    ] = useState<string | null>(
        null,
    );


    const [
        errorMessage,
        setErrorMessage,
    ] = useState<string | null>(
        null,
    );


    const [
        isSubmitting,
        setIsSubmitting,
    ] = useState(
        false,
    );


    const {
        categories,
        brands,
        models,

        isLoadingDictionaries,
        areModelsLoading,

        modelsBrandId,

        dictionaryErrorMessage,
        clearDictionaryError,
    } = useBotDictionaries(
        form.selectedBrandId,
    );


    const selectedCategory =
        categories.find(
            (category) =>
                String(category.id)
                === form.selectedCategoryId,
        ) ?? null;


    const selectedBrand =
        brands.find(
            (brand) =>
                String(brand.id)
                === form.selectedBrandId,
        ) ?? null;


    const selectedModel =
        models.find(
            (model) =>
                String(model.id)
                === form.selectedModelId,
        ) ?? null;


    const isStopped =
        bot !== null
        && bot.status
            .toUpperCase()
        === "STOPPED";


    const isFormInitialized =
        isBaseFormInitialized
        && isInitialModelResolved
        && initializationError === null;


    useEffect(
        () => {

            setBot(
                null,
            );

            setIsBaseFormInitialized(
                false,
            );

            setIsInitialModelResolved(
                false,
            );

            setInitializationError(
                null,
            );

            setErrorMessage(
                null,
            );


            if (
                !isBotIdValid
            ) {

                setIsLoadingBot(
                    false,
                );

                setInitializationError(
                    "Identyfikator bota w adresie jest nieprawidłowy.",
                );

                return;
            }


            let cancelled =
                false;


            async function loadBot() {

                setIsLoadingBot(
                    true,
                );


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

                        setInitializationError(
                            getErrorMessage(
                                error,
                                "Nie udało się pobrać konfiguracji bota.",
                            ),
                        );
                    }

                } finally {

                    if (
                        !cancelled
                    ) {

                        setIsLoadingBot(
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


    useEffect(
        () => {

            if (
                bot === null
                || isLoadingDictionaries
                || isBaseFormInitialized
                || initializationError !== null
            ) {

                return;
            }


            const matchingCategory =
                categories.find(
                    (category) =>
                        categoryPathsEqual(
                            category.categoryPath,
                            bot.configuration
                                .categoryPath,
                        ),
                );


            if (
                matchingCategory === undefined
            ) {

                setInitializationError(
                    "Nie udało się odnaleźć zapisanej kategorii bota w aktualnym słowniku.",
                );

                setIsBaseFormInitialized(
                    true,
                );

                setIsInitialModelResolved(
                    true,
                );

                return;
            }


            const matchingBrand =
                brands.find(
                    (brand) =>
                        normalizedText(
                            brand.name,
                        )
                        === normalizedText(
                            bot.configuration
                                .brand,
                        ),
                );


            if (
                matchingBrand === undefined
            ) {

                setInitializationError(
                    "Nie udało się odnaleźć zapisanej marki bota w aktualnym słowniku.",
                );

                setIsBaseFormInitialized(
                    true,
                );

                setIsInitialModelResolved(
                    true,
                );

                return;
            }


            const targetMode =
                resolveTargetMode(
                    bot,
                );


            const negotiationSteps =
                bot.configuration
                    .negotiationSteps
                    .slice()
                    .sort(
                        (
                            left,
                            right,
                        ) =>
                            left.stepNumber
                            - right.stepNumber,
                    )
                    .map(
                        (
                            step,
                            index,
                        ) => ({
                            id:
                                index + 1,

                            offerPrice:
                                String(
                                    step.offerPrice,
                                ),

                            maxAcceptedCounterOffer:
                                step.maxAcceptedCounterOffer
                                    === null
                                    ? ""
                                    : String(
                                        step.maxAcceptedCounterOffer,
                                    ),

                            message:
                                step.message,
                        }),
                    );


            const initializedSteps =
                negotiationSteps.length
                    > 0
                    ? negotiationSteps
                    : [
                        {
                            id: 1,
                            offerPrice: "",
                            maxAcceptedCounterOffer: "",
                            message: "",
                        },
                    ];


            const initialForm:
                CreateBotFormValues = {

                    botName:
                        bot.name,

                    email:
                        bot.email,

                    /*
                     * Backend celowo nie zwraca hasła.
                     * Puste pole podczas PATCH oznacza:
                     * zachowaj obecne hasło.
                     */
                    password:
                        "",

                    selectedCategoryId:
                        String(
                            matchingCategory.id,
                        ),

                    selectedBrandId:
                        String(
                            matchingBrand.id,
                        ),

                    targetMode,

                    selectedModelId:
                        "",

                    searchQuery:
                        bot.configuration
                            .searchQuery
                        ?? "",

                    minPrice:
                        String(
                            bot.configuration
                                .minPrice,
                        ),

                    maxPrice:
                        String(
                            bot.configuration
                                .maxPrice,
                        ),

                    autoRaiseOfferToVintedMinimum:
                        Boolean(
                            bot.configuration
                                .autoRaiseOfferToVintedMinimum,
                        ),

                    maxAutomaticOffer:
                        bot.configuration
                            .maxAutomaticOffer
                        === null
                            ? ""
                            : String(
                                bot.configuration
                                    .maxAutomaticOffer,
                            ),

                    dailyNegotiationBudget:
                        String(
                            bot.configuration
                                .dailyNegotiationBudget,
                        ),

                    negotiationSteps:
                        initializedSteps,
                };


            replaceForm(
                initialForm,
            );


            setIsBaseFormInitialized(
                true,
            );


            if (
                targetMode
                !== "VINTED_MODEL"
            ) {

                setIsInitialModelResolved(
                    true,
                );
            }

        },
        [
            bot,
            brands,
            categories,
            initializationError,
            isBaseFormInitialized,
            isLoadingDictionaries,
            replaceForm,
        ],
    );


    useEffect(
        () => {

            if (
                bot === null
                || !isBaseFormInitialized
                || isInitialModelResolved
                || form.targetMode
                    !== "VINTED_MODEL"
                || form.selectedBrandId
                    .length === 0
                || modelsBrandId
                    !== form.selectedBrandId
            ) {

                return;
            }


            const configuredModelName =
                bot.configuration
                    .model;


            if (
                configuredModelName === null
                || configuredModelName
                    .trim()
                    .length === 0
            ) {

                setInitializationError(
                    "Bot nie ma zapisanego modelu, mimo że korzysta z trybu VINTED_MODEL.",
                );

                setIsInitialModelResolved(
                    true,
                );

                return;
            }


            const matchingModel =
                models.find(
                    (model) =>
                        normalizedText(
                            model.name,
                        )
                        === normalizedText(
                            configuredModelName,
                        ),
                );


            if (
                matchingModel === undefined
            ) {

                setInitializationError(
                    "Nie udało się odnaleźć zapisanego modelu bota w aktualnym słowniku.",
                );

                setIsInitialModelResolved(
                    true,
                );

                return;
            }


            setModel(
                String(
                    matchingModel.id,
                ),
            );


            setIsInitialModelResolved(
                true,
            );

        },
        [
            bot,
            form.selectedBrandId,
            form.targetMode,
            isBaseFormInitialized,
            isInitialModelResolved,
            models,
            modelsBrandId,
            setModel,
        ],
    );


    function clearMessages() {

        setErrorMessage(
            null,
        );
    }


    function changeBotName(
        value: string,
    ) {

        setBotName(
            value,
        );

        clearMessages();
    }


    function changeEmail(
        value: string,
    ) {

        setEmail(
            value,
        );

        clearMessages();
    }


    function changePassword(
        value: string,
    ) {

        setPassword(
            value,
        );

        clearMessages();
    }


    function changeCategory(
        categoryId: string,
    ) {

        setCategory(
            categoryId,
        );

        clearMessages();
    }


    function changeBrand(
        brandId: string,
    ) {

        setBrand(
            brandId,
        );

        clearMessages();
        clearDictionaryError();
    }


    function changeTargetMode(
        targetMode: TargetMode,
    ) {

        setTargetMode(
            targetMode,
        );

        clearMessages();
    }


    function changeModel(
        modelId: string,
    ) {

        setModel(
            modelId,
        );

        clearMessages();
    }


    function changeSearchQuery(
        value: string,
    ) {

        setSearchQuery(
            value,
        );

        clearMessages();
    }


    function changeMinPrice(
        value: string,
    ) {

        setMinPrice(
            value,
        );

        clearMessages();
    }


    function changeMaxPrice(
        value: string,
    ) {

        setMaxPrice(
            value,
        );

        clearMessages();
    }


    function changeAutoRaiseOffer(
        value: boolean,
    ) {

        setAutoRaiseOfferToVintedMinimum(
            value,
        );

        clearMessages();
    }


    function changeMaxAutomaticOffer(
        value: string,
    ) {

        setMaxAutomaticOffer(
            value,
        );

        clearMessages();
    }


    function changeNegotiationBudget(
        value: string,
    ) {

        setDailyNegotiationBudget(
            value,
        );

        clearMessages();
    }


    function handleAddNegotiationStep() {

        const added =
            addNegotiationStep();


        if (
            !added
        ) {

            setErrorMessage(
                "Nie możesz dodać więcej niż 25 kroków negocjacji.",
            );

            return;
        }


        clearMessages();
    }


    function handleRemoveNegotiationStep(
        stepId: number,
    ) {

        const removed =
            removeNegotiationStep(
                stepId,
            );


        if (
            !removed
        ) {

            setErrorMessage(
                "Bot musi mieć przynajmniej jeden krok negocjacji.",
            );

            return;
        }


        clearMessages();
    }


    function handleUpdateNegotiationStep(
        stepId: number,
        field: NegotiationStepField,
        value: string,
    ) {

        updateNegotiationStep(
            stepId,
            field,
            value,
        );

        clearMessages();
    }


    async function handleSubmit(
        event:
            FormEvent<HTMLFormElement>,
    ) {

        event.preventDefault();


        if (
            isSubmitting
            || !isBotIdValid
            || bot === null
            || !isFormInitialized
        ) {

            return;
        }


        clearMessages();


        if (
            !isStopped
        ) {

            setErrorMessage(
                "Najpierw zatrzymaj bota. Konfigurację można edytować tylko dla zatrzymanego bota.",
            );

            return;
        }


        const validationResult =
            validateCreateBotForm({
                form,

                selectedCategory,
                selectedBrand,
                selectedModel,

                requirePassword:
                    false,
            });


        if (
            !validationResult.valid
        ) {

            setErrorMessage(
                validationResult
                    .errorMessage,
            );

            return;
        }


        const request =
            buildCreateBotRequest(
                validationResult.data,
            );


        setIsSubmitting(
            true,
        );


        try {

            await updateBot(
                botId,
                request,
            );


            navigate(
                "/bots",
            );

        } catch (error) {

            setErrorMessage(
                getErrorMessage(
                    error,
                    "Nie udało się zapisać zmian bota.",
                ),
            );

        } finally {

            setIsSubmitting(
                false,
            );
        }
    }


    if (
        isLoadingBot
        || (
            bot !== null
            && !isFormInitialized
            && initializationError === null
        )
    ) {

        return (
            <section className="page">

                <header className="page-header">

                    <div>

                        <p className="page-eyebrow">
                            Konfiguracja
                        </p>


                        <h1 className="page-title">
                            Edytuj bota
                        </h1>

                    </div>

                </header>


                <article className="content-card">

                    <div className="dictionary-list-state">
                        Pobieranie konfiguracji bota...
                    </div>

                </article>

            </section>
        );
    }


    if (
        bot === null
        || initializationError !== null
    ) {

        return (
            <section className="page">

                <header className="page-header">

                    <div>

                        <p className="page-eyebrow">
                            Konfiguracja
                        </p>


                        <h1 className="page-title">
                            Edytuj bota
                        </h1>

                    </div>

                </header>


                <div
                    className="form-message form-message-error"
                    role="alert"
                >
                    {
                        initializationError
                        ?? "Nie udało się pobrać bota."
                    }
                </div>


                <div className="bot-form-actions">

                    <Link
                        className="secondary-button"
                        to="/bots"
                    >
                        Wróć do botów
                    </Link>

                </div>

            </section>
        );
    }


    return (
        <section className="page">

            <header className="page-header">

                <div>

                    <p className="page-eyebrow">
                        Konfiguracja
                    </p>


                    <h1 className="page-title">
                        Edytuj bota #{bot.id}
                    </h1>


                    <p className="page-description">
                        Zmień konto Vinted, filtry,
                        strategię negocjacji lub limity.
                        Zapis jest możliwy wyłącznie
                        dla zatrzymanego bota bez
                        aktywnych negocjacji.
                    </p>

                </div>

            </header>


            {
                !isStopped
                && (
                    <div
                        className="form-message form-message-error"
                        role="alert"
                    >
                        Ten bot jest obecnie uruchomiony.
                        Najpierw zatrzymaj go na stronie
                        botów, aby odblokować edycję.
                    </div>
                )
            }


            <form
                className="bot-form"
                onSubmit={
                    handleSubmit
                }
            >

                <fieldset
                    className="bot-form-fieldset"
                    disabled={
                        isSubmitting
                        || !isStopped
                    }
                >

                    <BasicInfoSection
                        botName={
                            form.botName
                        }
                        onBotNameChange={
                            changeBotName
                        }
                    />


                    <VintedAccountSection
                        email={
                            form.email
                        }
                        password={
                            form.password
                        }
                        passwordOptional
                        onEmailChange={
                            changeEmail
                        }
                        onPasswordChange={
                            changePassword
                        }
                    />


                    <BotFiltersSection
                        categories={
                            categories
                        }
                        brands={
                            brands
                        }
                        models={
                            models
                        }
                        selectedCategoryId={
                            form.selectedCategoryId
                        }
                        selectedBrandId={
                            form.selectedBrandId
                        }
                        targetMode={
                            form.targetMode
                        }
                        selectedModelId={
                            form.selectedModelId
                        }
                        searchQuery={
                            form.searchQuery
                        }
                        minPrice={
                            form.minPrice
                        }
                        maxPrice={
                            form.maxPrice
                        }
                        isLoadingDictionaries={
                            isLoadingDictionaries
                        }
                        areModelsLoading={
                            areModelsLoading
                        }
                        onCategoryChange={
                            changeCategory
                        }
                        onBrandChange={
                            changeBrand
                        }
                        onTargetModeChange={
                            changeTargetMode
                        }
                        onModelChange={
                            changeModel
                        }
                        onSearchQueryChange={
                            changeSearchQuery
                        }
                        onMinPriceChange={
                            changeMinPrice
                        }
                        onMaxPriceChange={
                            changeMaxPrice
                        }
                    />


                    <NegotiationBudgetSection
                        dailyNegotiationBudget={
                            form.dailyNegotiationBudget
                        }
                        onBudgetChange={
                            changeNegotiationBudget
                        }
                    />


                    <OfferStrategySection
                        autoRaiseOfferToVintedMinimum={
                            form.autoRaiseOfferToVintedMinimum
                        }
                        maxAutomaticOffer={
                            form.maxAutomaticOffer
                        }
                        firstConfiguredOffer={
                            form.negotiationSteps[0]
                                ?.offerPrice
                            ?? ""
                        }
                        onAutoRaiseChange={
                            changeAutoRaiseOffer
                        }
                        onMaxAutomaticOfferChange={
                            changeMaxAutomaticOffer
                        }
                    />


                    <NegotiationStepsSection
                        negotiationSteps={
                            form.negotiationSteps
                        }
                        dailyNegotiationBudget={
                            form.dailyNegotiationBudget
                        }
                        onAddStep={
                            handleAddNegotiationStep
                        }
                        onRemoveStep={
                            handleRemoveNegotiationStep
                        }
                        onUpdateStep={
                            handleUpdateNegotiationStep
                        }
                    />

                </fieldset>


                {
                    dictionaryErrorMessage !== null
                    && (
                        <div
                            className="form-message form-message-error"
                            role="alert"
                        >
                            {dictionaryErrorMessage}
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

                    <Link
                        className="secondary-button"
                        to="/bots"
                    >
                        Anuluj
                    </Link>


                    <button
                        className="primary-button"
                        type="submit"
                        disabled={
                            isSubmitting
                            || isLoadingDictionaries
                            || areModelsLoading
                            || !isStopped
                        }
                    >
                        {
                            isSubmitting
                                ? "Zapisywanie..."
                                : "Zapisz zmiany"
                        }
                    </button>

                </div>

            </form>

        </section>
    );
}


function resolveTargetMode(
    bot: BotDetails,
): TargetMode {

    if (
        bot.configuration
            .targetMode !== null
    ) {

        return bot.configuration
            .targetMode;
    }


    if (
        bot.configuration
            .searchQuery !== null
        && bot.configuration
            .searchQuery
            .trim()
            .length > 0
    ) {

        return "SEARCH_QUERY";
    }


    return "VINTED_MODEL";
}


function categoryPathsEqual(
    left: string[],
    right: string[],
): boolean {

    if (
        left.length
        !== right.length
    ) {

        return false;
    }


    return left.every(
        (
            value,
            index,
        ) =>
            normalizedText(
                value,
            )
            === normalizedText(
                right[index],
            ),
    );
}


function normalizedText(
    value: string,
): string {

    return value
        .trim()
        .replace(
            /\s+/g,
            " ",
        )
        .toLocaleLowerCase();
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


export default EditBotPage;
