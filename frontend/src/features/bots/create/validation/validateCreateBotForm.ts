import type {
    DictionaryBrand,
    DictionaryCategory,
    DictionaryModel,
} from "../../../../types/dictionaries";

import type {
    CreateBotFormValidationResult,
    CreateBotFormValues,
    ValidatedNegotiationStep,
} from "../botForm";

interface ValidateCreateBotFormArguments {
    form: CreateBotFormValues;

    selectedCategory: DictionaryCategory | null;
    selectedBrand: DictionaryBrand | null;
    selectedModel: DictionaryModel | null;
}

export function validateCreateBotForm({
    form,
    selectedCategory,
    selectedBrand,
    selectedModel,
}: ValidateCreateBotFormArguments): CreateBotFormValidationResult {
    const normalizedBotName =
        normalizeText(
            form.botName,
        );

    if (normalizedBotName.length === 0) {
        return invalid(
            "Wpisz nazwę bota.",
        );
    }

    const normalizedEmail =
        form.email.trim();

    if (normalizedEmail.length === 0) {
        return invalid(
            "Wpisz e-mail konta Vinted.",
        );
    }

    if (form.password.length === 0) {
        return invalid(
            "Wpisz hasło konta Vinted.",
        );
    }

    if (selectedCategory === null) {
        return invalid(
            "Wybierz kategorię.",
        );
    }

    if (selectedBrand === null) {
        return invalid(
            "Wybierz markę.",
        );
    }

    if (selectedModel === null) {
        return invalid(
            "Wybierz model.",
        );
    }

    if (
        selectedModel.brandId
        !== selectedBrand.id
    ) {
        return invalid(
            "Wybrany model nie należy do wybranej marki.",
        );
    }

    const minPriceResult =
        parseRequiredNonNegativeNumber(
            form.minPrice,
            "Minimalna cena jest nieprawidłowa.",
        );

    if (!minPriceResult.valid) {
        return invalid(
            minPriceResult.errorMessage,
        );
    }

    const maxPriceResult =
        parseRequiredPositiveNumber(
            form.maxPrice,
            "Maksymalna cena jest nieprawidłowa.",
        );

    if (!maxPriceResult.valid) {
        return invalid(
            maxPriceResult.errorMessage,
        );
    }

    const minPrice =
        minPriceResult.value;

    const maxPrice =
        maxPriceResult.value;

    if (minPrice > maxPrice) {
        return invalid(
            "Minimalna cena nie może być większa od maksymalnej.",
        );
    }

    const dailyNegotiationBudget =
        Number(
            form.dailyNegotiationBudget,
        );

    if (
        !Number.isInteger(
            dailyNegotiationBudget,
        )
        || dailyNegotiationBudget < 1
        || dailyNegotiationBudget > 25
    ) {
        return invalid(
            "Dzienny budżet negocjacyjny musi być liczbą od 1 do 25.",
        );
    }

    if (
        form.negotiationSteps.length === 0
    ) {
        return invalid(
            "Bot musi mieć przynajmniej jeden krok negocjacji.",
        );
    }

    if (
        form.negotiationSteps.length
        > dailyNegotiationBudget
    ) {
        return invalid(
            "Liczba kroków negocjacji nie może być większa niż dzienny budżet.",
        );
    }

    const negotiationSteps:
        ValidatedNegotiationStep[] = [];

    for (
        let index = 0;
        index < form.negotiationSteps.length;
        index += 1
    ) {
        const step =
            form.negotiationSteps[index];

        const stepNumber =
            index + 1;

        const offerPriceResult =
            parseRequiredPositiveNumber(
                step.offerPrice,
                `Krok ${stepNumber}: cena oferty jest nieprawidłowa.`,
            );

        if (!offerPriceResult.valid) {
            return invalid(
                offerPriceResult.errorMessage,
            );
        }

        const counterOfferResult =
            parseRequiredPositiveNumber(
                step.maxAcceptedCounterOffer,
                `Krok ${stepNumber}: maksymalna akceptowalna kontroferta jest wymagana i musi być większa od 0.`,
            );

        if (!counterOfferResult.valid) {
            return invalid(
                counterOfferResult.errorMessage,
            );
        }

        const offerPrice =
            offerPriceResult.value;

        const maxAcceptedCounterOffer =
            counterOfferResult.value;

        if (
            maxAcceptedCounterOffer
            < offerPrice
        ) {
            return invalid(
                `Krok ${stepNumber}: maksymalna akceptowalna kontroferta nie może być niższa od ceny naszej oferty.`,
            );
        }

        const normalizedMessage =
            step.message.trim();

        if (
            normalizedMessage.length === 0
        ) {
            return invalid(
                `Krok ${stepNumber}: wiadomość jest wymagana.`,
            );
        }

        negotiationSteps.push({
            offerPrice,
            maxAcceptedCounterOffer,
            message:
                normalizedMessage,
        });
    }

    return {
        valid: true,

        data: {
            name:
                normalizedBotName,

            email:
                normalizedEmail,

            password:
                form.password,

            category:
                selectedCategory,

            brand:
                selectedBrand,

            model:
                selectedModel,

            minPrice,
            maxPrice,

            dailyNegotiationBudget,

            negotiationSteps,
        },
    };
}

interface ParsedNumberSuccess {
    valid: true;
    value: number;
}

interface ParsedNumberFailure {
    valid: false;
    errorMessage: string;
}

type ParsedNumberResult =
    | ParsedNumberSuccess
    | ParsedNumberFailure;

function parseRequiredPositiveNumber(
    value: string,
    errorMessage: string,
): ParsedNumberResult {
    if (
        value.trim().length === 0
    ) {
        return {
            valid: false,
            errorMessage,
        };
    }

    const parsedValue =
        Number(
            value,
        );

    if (
        !Number.isFinite(
            parsedValue,
        )
        || parsedValue <= 0
    ) {
        return {
            valid: false,
            errorMessage,
        };
    }

    return {
        valid: true,
        value:
            parsedValue,
    };
}

function parseRequiredNonNegativeNumber(
    value: string,
    errorMessage: string,
): ParsedNumberResult {
    if (
        value.trim().length === 0
    ) {
        return {
            valid: false,
            errorMessage,
        };
    }

    const parsedValue =
        Number(
            value,
        );

    if (
        !Number.isFinite(
            parsedValue,
        )
        || parsedValue < 0
    ) {
        return {
            valid: false,
            errorMessage,
        };
    }

    return {
        valid: true,
        value:
            parsedValue,
    };
}

function normalizeText(
    value: string,
): string {
    return value
        .trim()
        .replace(
            /\s+/g,
            " ",
        );
}

function invalid(
    errorMessage: string,
): CreateBotFormValidationResult {
    return {
        valid: false,
        errorMessage,
    };
}