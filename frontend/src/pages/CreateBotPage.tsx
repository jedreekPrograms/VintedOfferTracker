import {
    type FormEvent,
    useState,
} from "react";

import {
    useNavigate,
} from "react-router-dom";

import {
    createBot,
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
    NegotiationStepField,
} from "../features/bots/create/botForm";

import type {
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

function CreateBotPage() {
    const navigate =
        useNavigate();

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
    } = useCreateBotForm();

    const [
        errorMessage,
        setErrorMessage,
    ] = useState<string | null>(
        null,
    );

    const [
        isSubmitting,
        setIsSubmitting,
    ] = useState(false);

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

        if (isSubmitting) {
            return;
        }

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

        const request =
            buildCreateBotRequest(
                validationResult.data,
            );

        setIsSubmitting(
            true,
        );

        try {
            await createBot(
                request,
            );

            navigate(
                "/bots",
            );
        } catch (error) {
            setErrorMessage(
                getErrorMessage(
                    error,
                    "Nie udało się utworzyć bota.",
                ),
            );
        } finally {
            setIsSubmitting(
                false,
            );
        }
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
                <fieldset
                    className="bot-form-fieldset"
                    disabled={
                        isSubmitting
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

                <div className="bot-form-actions">
                    <button
                        className="primary-button"
                        type="submit"
                        disabled={
                            isSubmitting
                            || isLoadingDictionaries
                        }
                    >
                        {isSubmitting
                            ? "Tworzenie bota..."
                            : "Utwórz bota"}
                    </button>
                </div>
            </form>
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

export default CreateBotPage;
