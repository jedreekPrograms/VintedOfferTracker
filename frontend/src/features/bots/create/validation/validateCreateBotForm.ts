import type {
    DictionaryBrand,
    DictionaryCategory,
    DictionaryModel,
} from "../../../../types/dictionaries";

import type {
    NegotiationReactionAction,
} from "../../../../types/bots";

import type {
    CreateBotFormValidationResult,
    CreateBotFormValues,
    ValidatedCounterOfferRule,
    ValidatedNegotiationStep,
} from "../botForm";

interface ValidateCreateBotFormArguments {
    form: CreateBotFormValues;
    selectedCategory: DictionaryCategory | null;
    selectedBrand: DictionaryBrand | null;
    selectedModel: DictionaryModel | null;
    requirePassword?: boolean;
}

const MAX_RESPONSE_WAIT_HOURS = 24 * 30;

export function validateCreateBotForm({
    form,
    selectedCategory,
    selectedBrand,
    selectedModel,
    requirePassword = true,
}: ValidateCreateBotFormArguments): CreateBotFormValidationResult {
    const normalizedBotName = normalizeText(form.botName);
    if (normalizedBotName.length === 0) return invalid("Wpisz nazwę bota.");

    const normalizedEmail = form.email.trim();
    if (normalizedEmail.length === 0) return invalid("Wpisz e-mail konta Vinted.");
    if (requirePassword && form.password.length === 0) return invalid("Wpisz hasło konta Vinted.");

    if (selectedCategory === null) return invalid("Wybierz kategorię.");
    if (selectedBrand === null) return invalid("Wybierz markę.");
    if (selectedModel === null) return invalid("Wybierz model ze słownika.");
    if (selectedModel.brandId !== selectedBrand.id) return invalid("Wybrany model nie należy do wybranej marki.");

    const targetMode = selectedModel.targetMode;
    const validatedModel = targetMode === "VINTED_MODEL" ? selectedModel : null;
    const validatedSearchQuery = targetMode === "SEARCH_QUERY" ? normalizeText(selectedModel.name) : null;

    const minPriceResult = parseRequiredNonNegativeNumber(form.minPrice, "Minimalna cena jest nieprawidłowa.");
    if (!minPriceResult.valid) return invalid(minPriceResult.errorMessage);

    const maxPriceResult = parseRequiredPositiveNumber(form.maxPrice, "Maksymalna cena jest nieprawidłowa.");
    if (!maxPriceResult.valid) return invalid(maxPriceResult.errorMessage);

    const minPrice = minPriceResult.value;
    const maxPrice = maxPriceResult.value;
    if (minPrice > maxPrice) return invalid("Minimalna cena nie może być większa od maksymalnej.");

    let maxAutomaticOffer: number | null = null;
    if (form.autoRaiseOfferToVintedMinimum) {
        const result = parseRequiredPositiveNumber(
            form.maxAutomaticOffer,
            "Maksymalna cena negocjacji jest wymagana i musi być większa od 0.",
        );
        if (!result.valid) return invalid(result.errorMessage);
        maxAutomaticOffer = result.value;
        if (maxAutomaticOffer > maxPrice) {
            return invalid("Maksymalna cena negocjacji nie może być większa od maksymalnej ceny ogłoszenia.");
        }
    }

    const dailyNegotiationBudget = Number(form.dailyNegotiationBudget);
    if (!Number.isInteger(dailyNegotiationBudget) || dailyNegotiationBudget < 1 || dailyNegotiationBudget > 25) {
        return invalid("Dzienny budżet negocjacyjny musi być liczbą od 1 do 25.");
    }

    if (form.negotiationSteps.length === 0) return invalid("Bot musi mieć przynajmniej jeden krok negocjacji.");
    if (form.negotiationSteps.length > dailyNegotiationBudget) {
        return invalid("Liczba kroków negocjacji nie może być większa niż dzienny budżet.");
    }

    const negotiationSteps: ValidatedNegotiationStep[] = [];
    let previousOfferPrice: number | null = null;

    for (let index = 0; index < form.negotiationSteps.length; index += 1) {
        const step = form.negotiationSteps[index];
        const stepNumber = index + 1;

        const offerResult = parseRequiredPositiveNumber(step.offerPrice, `Krok ${stepNumber}: cena oferty jest nieprawidłowa.`);
        if (!offerResult.valid) return invalid(offerResult.errorMessage);

        const counterResult = parseRequiredPositiveNumber(
            step.maxAcceptedCounterOffer,
            `Krok ${stepNumber}: maksymalna akceptowalna kontroferta jest wymagana i musi być większa od 0.`,
        );
        if (!counterResult.valid) return invalid(counterResult.errorMessage);

        const offerPrice = offerResult.value;
        const maxAcceptedCounterOffer = counterResult.value;

        if (form.autoRaiseOfferToVintedMinimum && previousOfferPrice !== null && offerPrice <= previousOfferPrice) {
            return invalid(`Krok ${stepNumber}: cena oferty musi być wyższa niż w poprzednim kroku, ponieważ różnica procentowa służy do skalowania adaptacyjnej drabinki.`);
        }
        if (maxAcceptedCounterOffer < offerPrice) {
            return invalid(`Krok ${stepNumber}: maksymalna akceptowalna kontroferta nie może być niższa od ceny naszej oferty.`);
        }

        const normalizedMessage = step.message.trim();
        if (normalizedMessage.length === 0) return invalid(`Krok ${stepNumber}: wiadomość jest wymagana.`);

        const rejectionWait = validateWait(step.rejectionAction, step.rejectionWaitHours, `Krok ${stepNumber}: czas po odrzuceniu`);
        if (!rejectionWait.valid) return invalid(rejectionWait.errorMessage);

        const readWait = parseRequiredWaitHours(step.readWaitHours, `Krok ${stepNumber}: czas po odczytaniu`);
        if (!readWait.valid) return invalid(readWait.errorMessage);

        const unreadWait = parseRequiredWaitHours(step.unreadWaitHours, `Krok ${stepNumber}: czas bez odczytania`);
        if (!unreadWait.valid) return invalid(unreadWait.errorMessage);

        const counterFallbackWait = validateWait(
            step.counterOfferDefaultAction,
            step.counterOfferDefaultWaitHours,
            `Krok ${stepNumber}: domyślna reakcja na kontrofertę`,
        );
        if (!counterFallbackWait.valid) return invalid(counterFallbackWait.errorMessage);

        const validatedRules: ValidatedCounterOfferRule[] = [];
        const usedThresholds = new Set<number>();
        for (let ruleIndex = 0; ruleIndex < step.counterOfferRules.length; ruleIndex += 1) {
            const rule = step.counterOfferRules[ruleIndex];
            const thresholdResult = parseRequiredPositiveNumber(
                rule.minimumDiscountPercent,
                `Krok ${stepNumber}, reguła ${ruleIndex + 1}: próg obniżki jest nieprawidłowy.`,
            );
            if (!thresholdResult.valid) return invalid(thresholdResult.errorMessage);
            const threshold = thresholdResult.value;
            if (threshold > 100) return invalid(`Krok ${stepNumber}, reguła ${ruleIndex + 1}: próg obniżki nie może przekraczać 100%.`);
            if (usedThresholds.has(threshold)) return invalid(`Krok ${stepNumber}: próg ${threshold}% występuje więcej niż raz.`);
            usedThresholds.add(threshold);

            const waitResult = validateWait(rule.action, rule.waitHours, `Krok ${stepNumber}, reguła od ${threshold}%`);
            if (!waitResult.valid) return invalid(waitResult.errorMessage);
            validatedRules.push({ minimumDiscountPercent: threshold, action: rule.action, waitHours: waitResult.value });
        }

        validatedRules.sort((left, right) => left.minimumDiscountPercent - right.minimumDiscountPercent);
        negotiationSteps.push({
            offerPrice,
            maxAcceptedCounterOffer,
            message: normalizedMessage,
            rejectionAction: step.rejectionAction,
            rejectionWaitHours: rejectionWait.value,
            readWaitHours: readWait.value,
            unreadWaitHours: unreadWait.value,
            counterOfferDefaultAction: step.counterOfferDefaultAction,
            counterOfferDefaultWaitHours: counterFallbackWait.value,
            counterOfferRules: validatedRules,
        });
        previousOfferPrice = offerPrice;
    }

    if (form.autoRaiseOfferToVintedMinimum && maxAutomaticOffer !== null && negotiationSteps.length > 0
        && maxAutomaticOffer < negotiationSteps[0].offerPrice) {
        return invalid("Maksymalna cena negocjacji nie może być niższa od ceny pierwszego kroku negocjacji.");
    }

    return {
        valid: true,
        data: {
            name: normalizedBotName,
            email: normalizedEmail,
            password: form.password,
            category: selectedCategory,
            brand: selectedBrand,
            targetMode,
            model: validatedModel,
            searchQuery: validatedSearchQuery,
            minPrice,
            maxPrice,
            autoRaiseOfferToVintedMinimum: form.autoRaiseOfferToVintedMinimum,
            maxAutomaticOffer,
            dailyNegotiationBudget,
            negotiationSteps,
        },
    };
}

type ParsedNumberResult = { valid: true; value: number } | { valid: false; errorMessage: string };
type WaitValidationResult = { valid: true; value: number | null } | { valid: false; errorMessage: string };
type RequiredWaitValidationResult = { valid: true; value: number } | { valid: false; errorMessage: string };

function validateWait(action: NegotiationReactionAction, rawHours: string, label: string): WaitValidationResult {
    if (action === "NEXT_STEP_NOW") return { valid: true, value: null };
    const required = parseRequiredWaitHours(rawHours, label);
    return required.valid ? { valid: true, value: required.value } : required;
}

function parseRequiredWaitHours(rawHours: string, label: string): RequiredWaitValidationResult {
    const hours = Number(rawHours);
    if (!Number.isInteger(hours) || hours < 1 || hours > MAX_RESPONSE_WAIT_HOURS) {
        return {
            valid: false,
            errorMessage: `${label}: wpisz pełną liczbę godzin od 1 do ${MAX_RESPONSE_WAIT_HOURS}.`,
        };
    }
    return { valid: true, value: hours };
}

function parseRequiredPositiveNumber(value: string, errorMessage: string): ParsedNumberResult {
    if (value.trim().length === 0) return { valid: false, errorMessage };
    const parsedValue = Number(value);
    if (!Number.isFinite(parsedValue) || parsedValue <= 0) return { valid: false, errorMessage };
    return { valid: true, value: parsedValue };
}

function parseRequiredNonNegativeNumber(value: string, errorMessage: string): ParsedNumberResult {
    if (value.trim().length === 0) return { valid: false, errorMessage };
    const parsedValue = Number(value);
    if (!Number.isFinite(parsedValue) || parsedValue < 0) return { valid: false, errorMessage };
    return { valid: true, value: parsedValue };
}

function normalizeText(value: string): string {
    return value.trim().replace(/\s+/g, " ");
}

function invalid(errorMessage: string): CreateBotFormValidationResult {
    return { valid: false, errorMessage };
}
