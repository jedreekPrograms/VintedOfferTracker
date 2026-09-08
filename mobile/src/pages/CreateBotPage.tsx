import {
    type FormEvent,
    useState,
} from "react";
import { useNavigate } from "react-router-dom";

import { createBot } from "../api/botsApi";
import BasicInfoSection from "../features/bots/create/BasicInfoSection";
import BotFiltersSection from "../features/bots/create/BotFiltersSection";
import NegotiationBudgetSection from "../features/bots/create/NegotiationBudgetSection";
import NegotiationStepsSection from "../features/bots/create/NegotiationStepsSection";
import OfferStrategySection from "../features/bots/create/OfferStrategySection";
import VintedAccountSection from "../features/bots/create/VintedAccountSection";
import type {
    CounterOfferRuleField,
    NegotiationStepField,
    NegotiationStepPolicyField,
} from "../features/bots/create/botForm";
import type { TargetMode } from "../types/bots";
import { useBotDictionaries } from "../features/bots/create/hooks/useBotDictionaries";
import { useCreateBotForm } from "../features/bots/create/hooks/useCreateBotForm";
import { buildCreateBotRequest } from "../features/bots/create/mappers/buildCreateBotRequest";
import { validateCreateBotForm } from "../features/bots/create/validation/validateCreateBotForm";

function CreateBotPage() {
    const navigate = useNavigate();

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
        updateNegotiationStepPolicy,
        addCounterOfferRule,
        removeCounterOfferRule,
        updateCounterOfferRule,
    } = useCreateBotForm();

    const [errorMessage, setErrorMessage] = useState<string | null>(null);
    const [isSubmitting, setIsSubmitting] = useState(false);

    const {
        categories,
        brands,
        models,
        isLoadingDictionaries,
        areModelsLoading,
        dictionaryErrorMessage,
        clearDictionaryError,
    } = useBotDictionaries(form.selectedBrandId);

    const selectedCategory = categories.find(
        (category) => String(category.id) === form.selectedCategoryId,
    ) ?? null;
    const selectedBrand = brands.find(
        (brand) => String(brand.id) === form.selectedBrandId,
    ) ?? null;
    const selectedModel = models.find(
        (model) => String(model.id) === form.selectedModelId,
    ) ?? null;

    function clearMessages() {
        setErrorMessage(null);
    }

    function handleAddNegotiationStep() {
        if (!addNegotiationStep()) {
            setErrorMessage("Nie możesz dodać więcej niż 25 kroków negocjacji.");
            return;
        }
        clearMessages();
    }

    function handleRemoveNegotiationStep(stepId: number) {
        if (!removeNegotiationStep(stepId)) {
            setErrorMessage("Bot musi mieć przynajmniej jeden krok negocjacji.");
            return;
        }
        clearMessages();
    }

    function handleUpdateNegotiationStep(
        stepId: number,
        field: NegotiationStepField,
        value: string,
    ) {
        updateNegotiationStep(stepId, field, value);
        clearMessages();
    }

    function handleUpdateStepPolicy(
        stepId: number,
        field: NegotiationStepPolicyField,
        value: string,
    ) {
        updateNegotiationStepPolicy(stepId, field, value);
        clearMessages();
    }

    function handleAddCounterRule(stepId: number) {
        if (!addCounterOfferRule(stepId)) {
            setErrorMessage("Jeden krok może mieć maksymalnie 25 progów procentowych.");
            return;
        }
        clearMessages();
    }

    function handleUpdateCounterRule(
        stepId: number,
        ruleId: number,
        field: CounterOfferRuleField,
        value: string,
    ) {
        updateCounterOfferRule(stepId, ruleId, field, value);
        clearMessages();
    }

    async function handleSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        if (isSubmitting) {
            return;
        }

        clearMessages();
        const validationResult = validateCreateBotForm({
            form,
            selectedCategory,
            selectedBrand,
            selectedModel,
        });

        if (!validationResult.valid) {
            setErrorMessage(validationResult.errorMessage);
            return;
        }

        setIsSubmitting(true);
        try {
            await createBot(buildCreateBotRequest(validationResult.data));
            navigate("/bots");
        } catch (error) {
            setErrorMessage(
                getErrorMessage(error, "Nie udało się utworzyć bota."),
            );
        } finally {
            setIsSubmitting(false);
        }
    }

    return (
        <section className="page">
            <header className="page-header">
                <div>
                    <p className="page-eyebrow">Konfiguracja</p>
                    <h1 className="page-title">Utwórz bota</h1>
                    <p className="page-description">
                        Jeden bot korzysta z jednego konta Vinted i posiada
                        własne filtry, drabinkę cenową oraz politykę reakcji
                        na zachowanie sprzedającego.
                    </p>
                </div>
            </header>

            <form className="bot-form" onSubmit={handleSubmit}>
                <fieldset className="bot-form-fieldset" disabled={isSubmitting}>
                    <BasicInfoSection
                        botName={form.botName}
                        onBotNameChange={(value) => {
                            setBotName(value);
                            clearMessages();
                        }}
                    />

                    <VintedAccountSection
                        email={form.email}
                        password={form.password}
                        onEmailChange={(value) => {
                            setEmail(value);
                            clearMessages();
                        }}
                        onPasswordChange={(value) => {
                            setPassword(value);
                            clearMessages();
                        }}
                    />

                    <BotFiltersSection
                        categories={categories}
                        brands={brands}
                        models={models}
                        selectedCategoryId={form.selectedCategoryId}
                        selectedBrandId={form.selectedBrandId}
                        targetMode={form.targetMode}
                        selectedModelId={form.selectedModelId}
                        searchQuery={form.searchQuery}
                        minPrice={form.minPrice}
                        maxPrice={form.maxPrice}
                        isLoadingDictionaries={isLoadingDictionaries}
                        areModelsLoading={areModelsLoading}
                        onCategoryChange={(value) => {
                            setCategory(value);
                            clearMessages();
                        }}
                        onBrandChange={(value) => {
                            setBrand(value);
                            clearMessages();
                            clearDictionaryError();
                        }}
                        onTargetModeChange={(value: TargetMode) => {
                            setTargetMode(value);
                            clearMessages();
                        }}
                        onModelChange={(value) => {
                            setModel(value);
                            clearMessages();
                        }}
                        onSearchQueryChange={(value) => {
                            setSearchQuery(value);
                            clearMessages();
                        }}
                        onMinPriceChange={(value) => {
                            setMinPrice(value);
                            clearMessages();
                        }}
                        onMaxPriceChange={(value) => {
                            setMaxPrice(value);
                            clearMessages();
                        }}
                    />

                    <NegotiationBudgetSection
                        dailyNegotiationBudget={form.dailyNegotiationBudget}
                        onBudgetChange={(value) => {
                            setDailyNegotiationBudget(value);
                            clearMessages();
                        }}
                    />

                    <OfferStrategySection
                        autoRaiseOfferToVintedMinimum={
                            form.autoRaiseOfferToVintedMinimum
                        }
                        maxAutomaticOffer={form.maxAutomaticOffer}
                        firstConfiguredOffer={
                            form.negotiationSteps[0]?.offerPrice ?? ""
                        }
                        onAutoRaiseChange={(value) => {
                            setAutoRaiseOfferToVintedMinimum(value);
                            clearMessages();
                        }}
                        onMaxAutomaticOfferChange={(value) => {
                            setMaxAutomaticOffer(value);
                            clearMessages();
                        }}
                    />

                    <NegotiationStepsSection
                        negotiationSteps={form.negotiationSteps}
                        dailyNegotiationBudget={form.dailyNegotiationBudget}
                        onAddStep={handleAddNegotiationStep}
                        onRemoveStep={handleRemoveNegotiationStep}
                        onUpdateStep={handleUpdateNegotiationStep}
                        onUpdateStepPolicy={handleUpdateStepPolicy}
                        onAddCounterOfferRule={handleAddCounterRule}
                        onRemoveCounterOfferRule={(stepId, ruleId) => {
                            removeCounterOfferRule(stepId, ruleId);
                            clearMessages();
                        }}
                        onUpdateCounterOfferRule={handleUpdateCounterRule}
                    />
                </fieldset>

                {dictionaryErrorMessage !== null && (
                    <div className="form-message form-message-error" role="alert">
                        {dictionaryErrorMessage}
                    </div>
                )}

                {errorMessage !== null && (
                    <div className="form-message form-message-error" role="alert">
                        {errorMessage}
                    </div>
                )}

                <div className="bot-form-actions">
                    <button
                        className="primary-button"
                        type="submit"
                        disabled={isSubmitting || isLoadingDictionaries}
                    >
                        {isSubmitting ? "Tworzenie bota..." : "Utwórz bota"}
                    </button>
                </div>
            </form>
        </section>
    );
}

function getErrorMessage(error: unknown, fallbackMessage: string): string {
    return error instanceof Error ? error.message : fallbackMessage;
}

export default CreateBotPage;
