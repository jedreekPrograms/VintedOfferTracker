import {
    type FormEvent,
    useState,
} from "react";

import BasicInfoSection
    from "../features/bots/create/BasicInfoSection";

import BotFiltersSection
    from "../features/bots/create/BotFiltersSection";

import NegotiationBudgetSection
    from "../features/bots/create/NegotiationBudgetSection";

import NegotiationStepsSection
    from "../features/bots/create/NegotiationStepsSection";

import VintedAccountSection
    from "../features/bots/create/VintedAccountSection";

import {
    useBotDictionaries,
} from "../features/bots/create/hooks/useBotDictionaries";

import {
    useCreateBotForm,
} from "../features/bots/create/hooks/useCreateBotForm";

import {
    validateCreateBotForm,
} from "../features/bots/create/validation/validateCreateBotForm";

function CreateBotPage() {
    const {
        form,

        setBotName,
        setEmail,
        setPassword,

        setCategory,
        setBrand,
        setModel,

        setMinPrice,
        setMaxPrice,

        setDailyNegotiationBudget,

        addNegotiationStep,
        removeNegotiationStep,
        updateNegotiationStep,
    } = useCreateBotForm();

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

    function clearMessages() {
        setErrorMessage(null);
        setSuccessMessage(null);
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

    function changeModel(
        modelId: string,
    ) {
        setModel(
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

        if (!added) {
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

        if (!removed) {
            setErrorMessage(
                "Bot musi mieć przynajmniej jeden krok negocjacji.",
            );

            return;
        }

        clearMessages();
    }

    function handleUpdateNegotiationStep(
        stepId: number,
        field:
            | "offerPrice"
            | "maxAcceptedCounterOffer"
            | "message",
        value: string,
    ) {
        updateNegotiationStep(
            stepId,
            field,
            value,
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
                form,

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
         * Nadal nie wysyłamy danych.
         * Najpierw podłączymy dokładny
         * kontrakt backendowego POST /api/bots.
         *
         * Nie wypisujemy e-maila ani hasła.
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
                    selectedModelId={
                        form.selectedModelId
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

                <NegotiationBudgetSection
                    dailyNegotiationBudget={
                        form.dailyNegotiationBudget
                    }
                    onBudgetChange={
                        changeNegotiationBudget
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