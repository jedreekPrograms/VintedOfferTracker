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
    const navigate = useNavigate();
    const { botId: botIdParam } = useParams<{ botId: string }>();
    const botId = Number(botIdParam);
    const isBotIdValid = Number.isInteger(botId) && botId > 0;

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
        setAutoRaiseOfferToVintedMinimum,
        setMaxAutomaticOffer,
        setDailyNegotiationBudget,
        addNegotiationStep,
        removeNegotiationStep,
        updateNegotiationStep,
        replaceForm,
    } = useCreateBotForm();

    const [bot, setBot] = useState<BotDetails | null>(null);
    const [isLoadingBot, setIsLoadingBot] = useState(true);
    const [isBaseInitialized, setIsBaseInitialized] = useState(false);
    const [isModelResolved, setIsModelResolved] = useState(false);
    const [initializationError, setInitializationError] =
        useState<string | null>(null);
    const [errorMessage, setErrorMessage] =
        useState<string | null>(null);
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
        (category) => String(category.id) === form.selectedCategoryId,
    ) ?? null;

    const selectedBrand = brands.find(
        (brand) => String(brand.id) === form.selectedBrandId,
    ) ?? null;

    const selectedModel = models.find(
        (model) => String(model.id) === form.selectedModelId,
    ) ?? null;

    const isStopped = bot !== null
        && bot.status.toUpperCase() === "STOPPED";

    const isFormInitialized = isBaseInitialized
        && isModelResolved
        && initializationError === null;

    useEffect(() => {
        setBot(null);
        setIsBaseInitialized(false);
        setIsModelResolved(false);
        setInitializationError(null);
        setErrorMessage(null);

        if (!isBotIdValid) {
            setIsLoadingBot(false);
            setInitializationError("Identyfikator bota w adresie jest nieprawidłowy.");
            return;
        }

        let cancelled = false;

        async function loadBot() {
            setIsLoadingBot(true);

            try {
                const loadedBot = await getBot(botId);
                if (!cancelled) {
                    setBot(loadedBot);
                }
            } catch (error) {
                if (!cancelled) {
                    setInitializationError(
                        getErrorMessage(error, "Nie udało się pobrać konfiguracji bota."),
                    );
                }
            } finally {
                if (!cancelled) {
                    setIsLoadingBot(false);
                }
            }
        }

        void loadBot();

        return () => {
            cancelled = true;
        };
    }, [botId, isBotIdValid]);

    useEffect(() => {
        if (
            bot === null
            || isLoadingDictionaries
            || isBaseInitialized
            || initializationError !== null
        ) {
            return;
        }

        const matchingCategory = categories.find((category) =>
            categoryPathsEqual(
                category.categoryPath,
                bot.configuration.categoryPath,
            ),
        );

        if (matchingCategory === undefined) {
            setInitializationError(
                "Nie udało się odnaleźć zapisanej kategorii bota w aktualnym słowniku.",
            );
            setIsBaseInitialized(true);
            setIsModelResolved(true);
            return;
        }

        const matchingBrand = brands.find((brand) =>
            normalizedText(brand.name)
            === normalizedText(bot.configuration.brand),
        );

        if (matchingBrand === undefined) {
            setInitializationError(
                "Nie udało się odnaleźć zapisanej marki bota w aktualnym słowniku.",
            );
            setIsBaseInitialized(true);
            setIsModelResolved(true);
            return;
        }

        const targetMode = resolveTargetMode(bot);
        const steps = bot.configuration.negotiationSteps
            .slice()
            .sort((left, right) => left.stepNumber - right.stepNumber)
            .map((step, index) => ({
                id: index + 1,
                offerPrice: String(step.offerPrice),
                maxAcceptedCounterOffer:
                    step.maxAcceptedCounterOffer === null
                        ? ""
                        : String(step.maxAcceptedCounterOffer),
                message: step.message,
            }));

        const initialForm: CreateBotFormValues = {
            botName: bot.name,
            email: bot.email,
            password: "",
            selectedCategoryId: String(matchingCategory.id),
            selectedBrandId: String(matchingBrand.id),
            targetMode,
            selectedModelId: "",
            searchQuery: bot.configuration.searchQuery ?? "",
            minPrice: String(bot.configuration.minPrice),
            maxPrice: String(bot.configuration.maxPrice),
            autoRaiseOfferToVintedMinimum: Boolean(
                bot.configuration.autoRaiseOfferToVintedMinimum,
            ),
            maxAutomaticOffer:
                bot.configuration.maxAutomaticOffer === null
                    ? ""
                    : String(bot.configuration.maxAutomaticOffer),
            dailyNegotiationBudget: String(
                bot.configuration.dailyNegotiationBudget,
            ),
            negotiationSteps: steps.length > 0
                ? steps
                : [{
                    id: 1,
                    offerPrice: "",
                    maxAcceptedCounterOffer: "",
                    message: "",
                }],
        };

        replaceForm(initialForm);
        setIsBaseInitialized(true);
    }, [
        bot,
        brands,
        categories,
        initializationError,
        isBaseInitialized,
        isLoadingDictionaries,
        replaceForm,
    ]);

    useEffect(() => {
        if (
            bot === null
            || !isBaseInitialized
            || isModelResolved
            || form.selectedBrandId.length === 0
            || modelsBrandId !== form.selectedBrandId
            || areModelsLoading
        ) {
            return;
        }

        const targetMode = resolveTargetMode(bot);
        const configuredTargetName = targetMode === "SEARCH_QUERY"
            ? bot.configuration.searchQuery
            : bot.configuration.model;

        if (configuredTargetName === null || configuredTargetName.trim().length === 0) {
            setInitializationError(
                "Bot nie ma poprawnie zapisanego modelu / frazy wyszukiwania.",
            );
            setIsModelResolved(true);
            return;
        }

        const matchingModel = models.find((model) =>
            normalizedText(model.name) === normalizedText(configuredTargetName)
            && model.targetMode === targetMode,
        );

        if (matchingModel === undefined) {
            setInitializationError(
                `Nie znaleziono w słowniku modelu „${configuredTargetName}” z trybem ${
                    targetMode === "SEARCH_QUERY"
                        ? "wyszukiwanie tekstowe"
                        : "filtr Vinted"
                }. Dodaj lub popraw ten model w Słownikach przed edycją bota.`,
            );
            setIsModelResolved(true);
            return;
        }

        setModel(String(matchingModel.id));
        setIsModelResolved(true);
    }, [
        areModelsLoading,
        bot,
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

    async function handleSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();

        if (!isStopped || !isFormInitialized || isSubmitting) {
            return;
        }

        clearMessages();

        const validationResult = validateCreateBotForm({
            form,
            selectedCategory,
            selectedBrand,
            selectedModel,
            requirePassword: false,
        });

        if (!validationResult.valid) {
            setErrorMessage(validationResult.errorMessage);
            return;
        }

        const request = buildCreateBotRequest(validationResult.data);
        setIsSubmitting(true);

        try {
            await updateBot(botId, request);
            navigate("/bots");
        } catch (error) {
            setErrorMessage(
                getErrorMessage(error, "Nie udało się zapisać zmian bota."),
            );
        } finally {
            setIsSubmitting(false);
        }
    }

    function handleAddNegotiationStep() {
        if (!addNegotiationStep()) {
            setErrorMessage("Nie możesz dodać więcej niż 25 kroków negocjacji.");
        } else {
            clearMessages();
        }
    }

    function handleRemoveNegotiationStep(stepId: number) {
        if (!removeNegotiationStep(stepId)) {
            setErrorMessage("Bot musi mieć przynajmniej jeden krok negocjacji.");
        } else {
            clearMessages();
        }
    }

    function handleUpdateNegotiationStep(
        stepId: number,
        field: NegotiationStepField,
        value: string,
    ) {
        updateNegotiationStep(stepId, field, value);
        clearMessages();
    }

    if (isLoadingBot || isLoadingDictionaries) {
        return (
            <section className="page">
                <article className="content-card">
                    <div className="dictionary-list-state">Pobieranie konfiguracji bota...</div>
                </article>
            </section>
        );
    }

    if (initializationError !== null) {
        return (
            <section className="page">
                <div className="form-message form-message-error" role="alert">
                    {initializationError}
                </div>
                <Link className="secondary-button" to="/bots">Wróć do botów</Link>
            </section>
        );
    }

    if (bot === null) {
        return null;
    }

    return (
        <section className="page">
            <header className="page-header">
                <div>
                    <p className="page-eyebrow">Konfiguracja</p>
                    <h1 className="page-title">Edytuj bota</h1>
                    <p className="page-description">
                        Model jest teraz rozwiązywany przez słownik. Puste hasło oznacza zachowanie
                        obecnego hasła konta Vinted.
                    </p>
                </div>
            </header>

            {!isStopped && (
                <div className="form-message form-message-error" role="alert">
                    Zatrzymaj bota przed edycją konfiguracji.
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

            <form className="bot-form" onSubmit={handleSubmit}>
                <fieldset
                    className="bot-form-fieldset"
                    disabled={!isStopped || !isFormInitialized || isSubmitting}
                >
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
                        onTargetModeChange={() => undefined}
                        onModelChange={(value) => {
                            setModel(value);
                            clearMessages();
                        }}
                        onSearchQueryChange={() => undefined}
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
                        autoRaiseOfferToVintedMinimum={form.autoRaiseOfferToVintedMinimum}
                        maxAutomaticOffer={form.maxAutomaticOffer}
                        firstConfiguredOffer={form.negotiationSteps[0]?.offerPrice ?? ""}
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
                    />
                </fieldset>

                <div className="bot-form-actions">
                    <Link className="secondary-button" to="/bots">Anuluj</Link>
                    <button
                        className="primary-button"
                        type="submit"
                        disabled={!isStopped || !isFormInitialized || isSubmitting}
                    >
                        {isSubmitting ? "Zapisywanie..." : "Zapisz zmiany"}
                    </button>
                </div>
            </form>
        </section>
    );
}

function resolveTargetMode(bot: BotDetails): TargetMode {
    if (bot.configuration.targetMode !== null) {
        return bot.configuration.targetMode;
    }

    return bot.configuration.searchQuery !== null
        && bot.configuration.searchQuery.trim().length > 0
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

function getErrorMessage(error: unknown, fallbackMessage: string): string {
    return error instanceof Error
        ? error.message
        : fallbackMessage;
}

export default EditBotPage;
