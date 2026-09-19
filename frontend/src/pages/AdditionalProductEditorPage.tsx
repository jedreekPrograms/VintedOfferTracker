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

import { getBot } from "../api/botsApi";
import {
    createAdditionalTarget,
    updateAdditionalTarget,
} from "../api/additionalTargetsApi";
import BotFiltersSection from "../features/bots/create/BotFiltersSection";
import NegotiationStepsSection from "../features/bots/create/NegotiationStepsSection";
import OfferStrategySection from "../features/bots/create/OfferStrategySection";
import type {
    CounterOfferRuleField,
    CreateBotFormValues,
    NegotiationStepField,
    NegotiationStepForm,
    NegotiationStepPolicyField,
} from "../features/bots/create/botForm";
import { useBotDictionaries } from "../features/bots/create/hooks/useBotDictionaries";
import { useCreateBotForm } from "../features/bots/create/hooks/useCreateBotForm";
import { buildCreateBotRequest } from "../features/bots/create/mappers/buildCreateBotRequest";
import { validateCreateBotForm } from "../features/bots/create/validation/validateCreateBotForm";
import type {
    BotAdditionalTargetDetails,
    BotDetails,
    BotNegotiationStep,
    TargetMode,
    UpsertBotAdditionalTargetRequest,
} from "../types/bots";

const MAX_ADDITIONAL_PRODUCTS = 4;

function AdditionalProductEditorPage() {
    const navigate = useNavigate();
    const {
        botId: botIdParam,
        targetId: targetIdParam,
    } = useParams<{ botId: string; targetId?: string }>();

    const botId = Number(botIdParam);
    const editing = targetIdParam !== undefined;
    const targetId = editing ? Number(targetIdParam) : null;

    const {
        form,
        setCategory,
        setBrand,
        setTargetMode,
        setModel,
        setSearchQuery,
        setMinPrice,
        setMaxPrice,
        setAutoRaiseOfferToVintedMinimum,
        setMaxAutomaticOffer,
        addNegotiationStep,
        removeNegotiationStep,
        updateNegotiationStep,
        updateNegotiationStepPolicy,
        addCounterOfferRule,
        removeCounterOfferRule,
        updateCounterOfferRule,
        replaceForm,
    } = useCreateBotForm();

    const [bot, setBot] = useState<BotDetails | null>(null);
    const [editedTarget, setEditedTarget] =
        useState<BotAdditionalTargetDetails | null>(null);
    const [isLoadingBot, setIsLoadingBot] = useState(true);
    const [isBaseInitialized, setIsBaseInitialized] = useState(false);
    const [isModelResolved, setIsModelResolved] = useState(!editing);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);
    const [isSubmitting, setIsSubmitting] = useState(false);

    const {
        categories,
        brands,
        models,
        isLoadingDictionaries,
        areModelsLoading,
        modelsBrandId,
        dictionaryErrorMessage,
        clearDictionaryError,
    } = useBotDictionaries(form.selectedBrandId);

    const selectedCategory = categories.find(
        category => String(category.id) === form.selectedCategoryId,
    ) ?? null;
    const selectedBrand = brands.find(
        brand => String(brand.id) === form.selectedBrandId,
    ) ?? null;
    const selectedModel = models.find(
        model => String(model.id) === form.selectedModelId,
    ) ?? null;

    useEffect(() => {
        let cancelled = false;

        async function load() {
            if (!Number.isInteger(botId) || botId <= 0
                || (editing && (!Number.isInteger(targetId) || (targetId ?? 0) <= 0))) {
                setErrorMessage("Nieprawidłowy adres dodatkowego produktu.");
                setIsLoadingBot(false);
                return;
            }

            setIsLoadingBot(true);
            try {
                const loadedBot = await getBot(botId);
                if (cancelled) {
                    return;
                }

                setBot(loadedBot);
                if (editing) {
                    const target = (loadedBot.additionalTargets ?? []).find(
                        candidate => candidate.additionalTargetId === targetId,
                    ) ?? null;
                    if (target === null) {
                        setErrorMessage("Nie znaleziono aktywnego dodatkowego produktu.");
                    }
                    setEditedTarget(target);
                }
            } catch (error) {
                if (!cancelled) {
                    setErrorMessage(getErrorMessage(
                        error,
                        "Nie udało się pobrać konfiguracji bota.",
                    ));
                }
            } finally {
                if (!cancelled) {
                    setIsLoadingBot(false);
                }
            }
        }

        void load();
        return () => {
            cancelled = true;
        };
    }, [botId, editing, targetId]);

    useEffect(() => {
        if (bot === null
            || isLoadingDictionaries
            || isBaseInitialized
            || errorMessage !== null) {
            return;
        }

        if (!editing) {
            replaceForm(newProductForm(bot));
            setIsBaseInitialized(true);
            setIsModelResolved(true);
            return;
        }

        if (editedTarget === null) {
            return;
        }

        const category = categories.find(candidate =>
            categoryPathsEqual(candidate.categoryPath, editedTarget.categoryPath),
        );
        const brand = brands.find(candidate =>
            normalizedText(candidate.name) === normalizedText(editedTarget.brand),
        );

        if (category === undefined || brand === undefined) {
            setErrorMessage(
                "Nie udało się dopasować zapisanej kategorii lub marki do aktualnego słownika.",
            );
            return;
        }

        replaceForm(existingProductForm(
            bot,
            editedTarget,
            String(category.id),
            String(brand.id),
        ));
        setIsBaseInitialized(true);
    }, [
        bot,
        brands,
        categories,
        editedTarget,
        editing,
        errorMessage,
        isBaseInitialized,
        isLoadingDictionaries,
        replaceForm,
    ]);

    useEffect(() => {
        if (!editing
            || editedTarget === null
            || !isBaseInitialized
            || isModelResolved
            || areModelsLoading
            || modelsBrandId !== form.selectedBrandId) {
            return;
        }

        const mode = resolveTargetMode(editedTarget);
        const name = mode === "SEARCH_QUERY"
            ? editedTarget.searchQuery
            : editedTarget.model;
        const matchingModel = models.find(model =>
            model.targetMode === mode
            && normalizedText(model.name) === normalizedText(name ?? ""),
        );

        if (matchingModel === undefined) {
            setErrorMessage(
                `Nie znaleziono zapisanego modelu „${name ?? ""}” w aktualnym słowniku.`,
            );
            setIsModelResolved(true);
            return;
        }

        setModel(String(matchingModel.id));
        setIsModelResolved(true);
    }, [
        areModelsLoading,
        editedTarget,
        editing,
        form.selectedBrandId,
        isBaseInitialized,
        isModelResolved,
        models,
        modelsBrandId,
        setModel,
    ]);

    function clearMessages() {
        setErrorMessage(null);
    }

    function copyMainStrategy() {
        if (bot === null) {
            return;
        }

        replaceForm({
            ...form,
            autoRaiseOfferToVintedMinimum: Boolean(
                bot.configuration.autoRaiseOfferToVintedMinimum,
            ),
            maxAutomaticOffer: bot.configuration.maxAutomaticOffer === null
                ? ""
                : String(bot.configuration.maxAutomaticOffer),
            negotiationSteps: mapSteps(bot.configuration.negotiationSteps),
        });
        clearMessages();
    }

    async function handleSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        if (bot === null || isSubmitting || !isBaseInitialized || !isModelResolved) {
            return;
        }

        if (bot.status.toUpperCase() !== "STOPPED") {
            setErrorMessage("Zatrzymaj bota przed zmianą dodatkowych produktów.");
            return;
        }

        if (!editing
            && (bot.additionalTargets ?? []).length >= MAX_ADDITIONAL_PRODUCTS) {
            setErrorMessage("Bot ma już maksymalnie 4 dodatkowe produkty.");
            return;
        }

        const validation = validateCreateBotForm({
            form,
            selectedCategory,
            selectedBrand,
            selectedModel,
            requirePassword: false,
        });

        if (!validation.valid) {
            setErrorMessage(validation.errorMessage);
            return;
        }

        const configuration = buildCreateBotRequest(validation.data).configuration;
        const request: UpsertBotAdditionalTargetRequest = {
            categoryPath: configuration.categoryPath,
            brand: configuration.brand,
            targetMode: configuration.targetMode,
            model: configuration.model,
            searchQuery: configuration.searchQuery,
            minPrice: configuration.minPrice,
            maxPrice: configuration.maxPrice,
            autoRaiseOfferToVintedMinimum:
                configuration.autoRaiseOfferToVintedMinimum,
            maxAutomaticOffer: configuration.maxAutomaticOffer,
            negotiationSteps: configuration.negotiationSteps,
        };

        setIsSubmitting(true);
        setErrorMessage(null);
        try {
            if (editing && targetId !== null) {
                await updateAdditionalTarget(botId, targetId, request);
            } else {
                await createAdditionalTarget(botId, request);
            }
            navigate(`/bots/${botId}/edit`);
        } catch (error) {
            setErrorMessage(getErrorMessage(
                error,
                "Nie udało się zapisać dodatkowego produktu.",
            ));
        } finally {
            setIsSubmitting(false);
        }
    }

    if (isLoadingBot || isLoadingDictionaries) {
        return (
            <section className="page">
                <article className="content-card">
                    <div className="dictionary-list-state">
                        Pobieranie konfiguracji produktu...
                    </div>
                </article>
            </section>
        );
    }

    if (bot === null || errorMessage !== null && !isBaseInitialized) {
        return (
            <section className="page">
                {errorMessage !== null && (
                    <div className="form-message form-message-error" role="alert">
                        {errorMessage}
                    </div>
                )}
                <Link className="secondary-button" to={`/bots/${botId}/edit`}>
                    Wróć do bota
                </Link>
            </section>
        );
    }

    const stopped = bot.status.toUpperCase() === "STOPPED";
    const ready = isBaseInitialized && isModelResolved;

    return (
        <section className="page">
            <header className="page-header">
                <div>
                    <p className="page-eyebrow">Dodatkowy produkt</p>
                    <h1 className="page-title">
                        {editing ? "Edytuj produkt" : "Dodaj produkt"}
                    </h1>
                    <p className="page-description">
                        Konto Vinted, hasło, sesja i dzienny budżet pozostają wspólne
                        z botem „{bot.name}”. Tutaj ustawiasz wyłącznie osobny cel,
                        ceny i strategię negocjacji tego produktu.
                    </p>
                </div>
            </header>

            {!stopped && (
                <div className="form-message form-message-error" role="alert">
                    Zatrzymaj bota przed dodawaniem lub edycją dodatkowego produktu.
                </div>
            )}
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

            <div className="information-box">
                <strong>Wspólny dzienny budżet:</strong>{" "}
                {bot.configuration.dailyNegotiationBudget} akcji dla całego konta,
                niezależnie od liczby produktów.
            </div>

            <form className="bot-form" onSubmit={handleSubmit}>
                <fieldset
                    className="bot-form-fieldset"
                    disabled={!stopped || !ready || isSubmitting}
                >
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

                    <article className="content-card">
                        <div className="bot-form-section-header">
                            <div>
                                <span className="bot-form-step">4</span>
                                <h2 className="content-card-title">Strategia produktu</h2>
                            </div>
                            <button
                                className="secondary-button"
                                type="button"
                                onClick={copyMainStrategy}
                            >
                                Skopiuj strategię z produktu głównego
                            </button>
                        </div>
                    </article>

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
                        dailyNegotiationBudget={String(
                            bot.configuration.dailyNegotiationBudget,
                        )}
                        onAddStep={() => {
                            if (!addNegotiationStep()) {
                                setErrorMessage("Nie możesz dodać więcej niż 25 kroków negocjacji.");
                            } else {
                                clearMessages();
                            }
                        }}
                        onRemoveStep={(stepId) => {
                            if (!removeNegotiationStep(stepId)) {
                                setErrorMessage("Produkt musi mieć przynajmniej jeden krok negocjacji.");
                            } else {
                                clearMessages();
                            }
                        }}
                        onUpdateStep={(
                            stepId: number,
                            field: NegotiationStepField,
                            value: string,
                        ) => {
                            updateNegotiationStep(stepId, field, value);
                            clearMessages();
                        }}
                        onUpdateStepPolicy={(
                            stepId: number,
                            field: NegotiationStepPolicyField,
                            value: string,
                        ) => {
                            updateNegotiationStepPolicy(stepId, field, value);
                            clearMessages();
                        }}
                        onAddCounterOfferRule={(stepId) => {
                            if (!addCounterOfferRule(stepId)) {
                                setErrorMessage("Jeden krok może mieć maksymalnie 25 progów procentowych.");
                            } else {
                                clearMessages();
                            }
                        }}
                        onRemoveCounterOfferRule={(stepId, ruleId) => {
                            removeCounterOfferRule(stepId, ruleId);
                            clearMessages();
                        }}
                        onUpdateCounterOfferRule={(
                            stepId: number,
                            ruleId: number,
                            field: CounterOfferRuleField,
                            value: string,
                        ) => {
                            updateCounterOfferRule(stepId, ruleId, field, value);
                            clearMessages();
                        }}
                    />
                </fieldset>

                <div className="bot-form-actions">
                    <Link className="secondary-button" to={`/bots/${botId}/edit`}>
                        Anuluj
                    </Link>
                    <button
                        className="primary-button"
                        type="submit"
                        disabled={!stopped || !ready || isSubmitting}
                    >
                        {isSubmitting
                            ? "Zapisywanie..."
                            : editing
                                ? "Zapisz produkt"
                                : "Dodaj produkt"}
                    </button>
                </div>
            </form>
        </section>
    );
}

function newProductForm(bot: BotDetails): CreateBotFormValues {
    return {
        botName: bot.name,
        email: bot.email,
        password: "",
        selectedCategoryId: "",
        selectedBrandId: "",
        targetMode: "VINTED_MODEL",
        selectedModelId: "",
        searchQuery: "",
        minPrice: "",
        maxPrice: "",
        autoRaiseOfferToVintedMinimum: false,
        maxAutomaticOffer: "",
        dailyNegotiationBudget: String(bot.configuration.dailyNegotiationBudget),
        negotiationSteps: [blankStep()],
    };
}

function existingProductForm(
    bot: BotDetails,
    target: BotAdditionalTargetDetails,
    categoryId: string,
    brandId: string,
): CreateBotFormValues {
    return {
        botName: bot.name,
        email: bot.email,
        password: "",
        selectedCategoryId: categoryId,
        selectedBrandId: brandId,
        targetMode: resolveTargetMode(target),
        selectedModelId: "",
        searchQuery: target.searchQuery ?? "",
        minPrice: String(target.minPrice),
        maxPrice: String(target.maxPrice),
        autoRaiseOfferToVintedMinimum: Boolean(
            target.autoRaiseOfferToVintedMinimum,
        ),
        maxAutomaticOffer: target.maxAutomaticOffer === null
            ? ""
            : String(target.maxAutomaticOffer),
        dailyNegotiationBudget: String(bot.configuration.dailyNegotiationBudget),
        negotiationSteps: mapSteps(target.negotiationSteps),
    };
}

function mapSteps(steps: BotNegotiationStep[]): NegotiationStepForm[] {
    return steps
        .slice()
        .sort((left, right) => left.stepNumber - right.stepNumber)
        .map((step, index) => ({
            id: index + 1,
            offerPrice: String(step.offerPrice),
            maxAcceptedCounterOffer: step.maxAcceptedCounterOffer === null
                ? ""
                : String(step.maxAcceptedCounterOffer),
            message: step.message,
            rejectionAction: step.rejectionAction ?? "NEXT_STEP_NOW",
            rejectionWaitHours: step.rejectionWaitHours === null
                ? ""
                : String(step.rejectionWaitHours),
            counterOfferDefaultAction:
                step.counterOfferDefaultAction ?? "WAIT_BEFORE_NEXT_STEP",
            counterOfferDefaultWaitHours:
                step.counterOfferDefaultWaitHours === null
                    ? ""
                    : String(step.counterOfferDefaultWaitHours),
            counterOfferRules: (step.counterOfferRules ?? []).map(
                (rule, ruleIndex) => ({
                    id: (index + 1) * 100 + ruleIndex + 1,
                    minimumDiscountPercent: String(rule.minimumDiscountPercent),
                    action: rule.action,
                    waitHours: rule.waitHours === null
                        ? ""
                        : String(rule.waitHours),
                }),
            ),
        }));
}

function blankStep(): NegotiationStepForm {
    return {
        id: 1,
        offerPrice: "",
        maxAcceptedCounterOffer: "",
        message: "",
        rejectionAction: "NEXT_STEP_NOW",
        rejectionWaitHours: "",
        counterOfferDefaultAction: "WAIT_BEFORE_NEXT_STEP",
        counterOfferDefaultWaitHours: "6",
        counterOfferRules: [
            {
                id: 1,
                minimumDiscountPercent: "10",
                action: "WAIT_BEFORE_NEXT_STEP",
                waitHours: "2",
            },
            {
                id: 2,
                minimumDiscountPercent: "15",
                action: "NEXT_STEP_NOW",
                waitHours: "",
            },
        ],
    };
}

function resolveTargetMode(target: BotAdditionalTargetDetails): TargetMode {
    if (target.targetMode !== null) {
        return target.targetMode;
    }
    return target.searchQuery !== null && target.searchQuery.trim().length > 0
        ? "SEARCH_QUERY"
        : "VINTED_MODEL";
}

function categoryPathsEqual(left: string[], right: string[]): boolean {
    return left.length === right.length
        && left.every((element, index) =>
            normalizedText(element) === normalizedText(right[index] ?? ""),
        );
}

function normalizedText(value: string): string {
    return value.trim().replace(/\s+/g, " ").toLowerCase();
}

function getErrorMessage(error: unknown, fallback: string): string {
    return error instanceof Error ? error.message : fallback;
}

export default AdditionalProductEditorPage;
