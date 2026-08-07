import {
    type FormEvent,
    useRef,
    useState,
} from "react";

import BotFiltersSection
    from "../features/bots/create/BotFiltersSection";

import NegotiationStepsSection
    from "../features/bots/create/NegotiationStepsSection";

import type {
    NegotiationStepForm,
} from "../features/bots/create/botForm";

import {
    useBotDictionaries,
} from "../features/bots/create/hooks/useBotDictionaries";

import {
    validateCreateBotForm,
} from "../features/bots/create/validation/validateCreateBotForm";

function CreateBotPage() {
    const [
        botName,
        setBotName,
    ] = useState("");

    const [
        email,
        setEmail,
    ] = useState("");

    const [
        password,
        setPassword,
    ] = useState("");

    const [
        selectedCategoryId,
        setSelectedCategoryId,
    ] = useState("");

    const [
        selectedBrandId,
        setSelectedBrandId,
    ] = useState("");

    const [
        selectedModelId,
        setSelectedModelId,
    ] = useState("");

    const [
        minPrice,
        setMinPrice,
    ] = useState("");

    const [
        maxPrice,
        setMaxPrice,
    ] = useState("");

    const [
        dailyNegotiationBudget,
        setDailyNegotiationBudget,
    ] = useState("25");

    const [
        negotiationSteps,
        setNegotiationSteps,
    ] = useState<NegotiationStepForm[]>([
        {
            id: 1,
            offerPrice: "",
            maxAcceptedCounterOffer: "",
            message: "",
        },
    ]);

    const nextNegotiationStepId =
        useRef(2);

    const [
        errorMessage,
        setErrorMessage,
    ] = useState<string | null>(
        null,
    );

    const [
        successMessage,
        setSuccessMessage,
    ] = useState<string | null>(
        null,
    );

    const {
        categories,
        brands,
        models,

        isLoadingDictionaries,
        areModelsLoading,

        dictionaryErrorMessage,
        clearDictionaryError,
    } = useBotDictionaries(
        selectedBrandId,
    );

    const selectedCategory =
        categories.find(
            (category) =>
                String(category.id)
                === selectedCategoryId,
        ) ?? null;

    const selectedBrand =
        brands.find(
            (brand) =>
                String(brand.id)
                === selectedBrandId,
        ) ?? null;

    const selectedModel =
        models.find(
            (model) =>
                String(model.id)
                === selectedModelId,
        ) ?? null;

    function clearMessages() {
        setErrorMessage(null);
        setSuccessMessage(null);
    }

    function changeCategory(
        categoryId: string,
    ) {
        setSelectedCategoryId(
            categoryId,
        );

        clearMessages();
    }

    function changeBrand(
        brandId: string,
    ) {
        setSelectedBrandId(
            brandId,
        );

        setSelectedModelId("");

        clearMessages();
        clearDictionaryError();
    }

    function changeModel(
        modelId: string,
    ) {
        setSelectedModelId(
            modelId,
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

    function addNegotiationStep() {
        if (
            negotiationSteps.length >= 25
        ) {
            setErrorMessage(
                "Nie możesz dodać więcej niż 25 kroków negocjacji.",
            );

            return;
        }

        const newStep:
            NegotiationStepForm = {
                id:
                    nextNegotiationStepId
                        .current,

                offerPrice: "",

                maxAcceptedCounterOffer:
                    "",

                message: "",
            };

        nextNegotiationStepId.current +=
            1;

        setNegotiationSteps(
            (currentSteps) => [
                ...currentSteps,
                newStep,
            ],
        );

        clearMessages();
    }

    function removeNegotiationStep(
        stepId: number,
    ) {
        if (
            negotiationSteps.length === 1
        ) {
            setErrorMessage(
                "Bot musi mieć przynajmniej jeden krok negocjacji.",
            );

            return;
        }

        setNegotiationSteps(
            (currentSteps) =>
                currentSteps.filter(
                    (step) =>
                        step.id
                        !== stepId,
                ),
        );

        clearMessages();
    }

    function updateNegotiationStep(
        stepId: number,
        field:
            | "offerPrice"
            | "maxAcceptedCounterOffer"
            | "message",
        value: string,
    ) {
        setNegotiationSteps(
            (currentSteps) =>
                currentSteps.map(
                    (step) => {
                        if (
                            step.id
                            !== stepId
                        ) {
                            return step;
                        }

                        return {
                            ...step,
                            [field]: value,
                        };
                    },
                ),
        );

        clearMessages();
    }

    function handleSubmit(
        event:
            FormEvent<HTMLFormElement>,
    ) {
        event.preventDefault();

        clearMessages();

        const validationResult =
            validateCreateBotForm({
                form: {
                    botName,

                    email,
                    password,

                    selectedCategoryId,
                    selectedBrandId,
                    selectedModelId,

                    minPrice,
                    maxPrice,

                    dailyNegotiationBudget,

                    negotiationSteps,
                },

                selectedCategory,
                selectedBrand,
                selectedModel,
            });

        if (!validationResult.valid) {
            setErrorMessage(
                validationResult.errorMessage,
            );

            return;
        }

        const validatedData =
            validationResult.data;

        /*
         * Na razie tylko sprawdzamy wynik.
         *
         * Nie logujemy:
         * - e-maila,
         * - hasła.
         */
        console.log(
            "Validated bot configuration:",
            {
                name:
                    validatedData.name,

                categoryPath:
                    validatedData
                        .category
                        .categoryPath,

                brand:
                    validatedData
                        .brand
                        .name,

                model:
                    validatedData
                        .model
                        .name,

                minPrice:
                    validatedData
                        .minPrice,

                maxPrice:
                    validatedData
                        .maxPrice,

                dailyNegotiationBudget:
                    validatedData
                        .dailyNegotiationBudget,

                negotiationSteps:
                    validatedData
                        .negotiationSteps,
            },
        );

        setSuccessMessage(
            "Konfiguracja jest poprawna.",
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
                        Utwórz bota
                    </h1>

                    <p className="page-description">
                        Jeden bot korzysta
                        z jednego konta Vinted
                        i posiada własne filtry
                        oraz strategię negocjacji.
                    </p>
                </div>
            </header>

            <form
                className="bot-form"
                onSubmit={
                    handleSubmit
                }
            >
                <article className="content-card">
                    <div className="bot-form-section-header">
                        <div>
                            <span className="bot-form-step">
                                1
                            </span>

                            <h2 className="content-card-title">
                                Podstawowe informacje
                            </h2>
                        </div>
                    </div>

                    <div className="form-field">
                        <label
                            className="form-label"
                            htmlFor="bot-name"
                        >
                            Nazwa bota
                        </label>

                        <input
                            id="bot-name"
                            className="form-input"
                            value={
                                botName
                            }
                            placeholder="np. Samsung S25"
                            onChange={(event) => {
                                setBotName(
                                    event.target.value,
                                );

                                clearMessages();
                            }}
                        />
                    </div>
                </article>

                <article className="content-card">
                    <div className="bot-form-section-header">
                        <div>
                            <span className="bot-form-step">
                                2
                            </span>

                            <h2 className="content-card-title">
                                Konto Vinted
                            </h2>
                        </div>
                    </div>

                    <div className="bot-form-grid bot-form-grid-two">
                        <div className="form-field">
                            <label
                                className="form-label"
                                htmlFor="vinted-email"
                            >
                                E-mail
                            </label>

                            <input
                                id="vinted-email"
                                className="form-input"
                                type="email"
                                value={
                                    email
                                }
                                autoComplete="username"
                                onChange={(event) => {
                                    setEmail(
                                        event.target.value,
                                    );

                                    clearMessages();
                                }}
                            />
                        </div>

                        <div className="form-field">
                            <label
                                className="form-label"
                                htmlFor="vinted-password"
                            >
                                Hasło
                            </label>

                            <input
                                id="vinted-password"
                                className="form-input"
                                type="password"
                                value={
                                    password
                                }
                                autoComplete="current-password"
                                onChange={(event) => {
                                    setPassword(
                                        event.target.value,
                                    );

                                    clearMessages();
                                }}
                            />
                        </div>
                    </div>
                </article>

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
                        selectedCategoryId
                    }
                    selectedBrandId={
                        selectedBrandId
                    }
                    selectedModelId={
                        selectedModelId
                    }
                    minPrice={
                        minPrice
                    }
                    maxPrice={
                        maxPrice
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
                    onModelChange={
                        changeModel
                    }
                    onMinPriceChange={
                        changeMinPrice
                    }
                    onMaxPriceChange={
                        changeMaxPrice
                    }
                />

                <article className="content-card">
                    <div className="bot-form-section-header">
                        <div>
                            <span className="bot-form-step">
                                4
                            </span>

                            <h2 className="content-card-title">
                                Budżet negocjacyjny
                            </h2>
                        </div>
                    </div>

                    <div className="form-field bot-budget-field">
                        <label
                            className="form-label"
                            htmlFor="negotiation-budget"
                        >
                            Dzienny budżet
                        </label>

                        <input
                            id="negotiation-budget"
                            className="form-input"
                            type="number"
                            min="1"
                            max="25"
                            step="1"
                            value={
                                dailyNegotiationBudget
                            }
                            onChange={(event) => {
                                setDailyNegotiationBudget(
                                    event.target.value,
                                );

                                clearMessages();
                            }}
                        />
                    </div>
                </article>

                <NegotiationStepsSection
                    negotiationSteps={
                        negotiationSteps
                    }
                    dailyNegotiationBudget={
                        dailyNegotiationBudget
                    }
                    onAddStep={
                        addNegotiationStep
                    }
                    onRemoveStep={
                        removeNegotiationStep
                    }
                    onUpdateStep={
                        updateNegotiationStep
                    }
                />

                {dictionaryErrorMessage !== null && (
                    <div
                        className="form-message form-message-error"
                        role="alert"
                    >
                        {dictionaryErrorMessage}
                    </div>
                )}

                {errorMessage !== null && (
                    <div
                        className="form-message form-message-error"
                        role="alert"
                    >
                        {errorMessage}
                    </div>
                )}

                {successMessage !== null && (
                    <div
                        className="form-message form-message-success"
                        role="status"
                    >
                        {successMessage}
                    </div>
                )}

                <div className="bot-form-actions">
                    <button
                        className="primary-button"
                        type="submit"
                    >
                        Sprawdź konfigurację
                    </button>
                </div>
            </form>
        </section>
    );
}

export default CreateBotPage;