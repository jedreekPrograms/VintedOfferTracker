import type {
    CreateBotRequest,
} from "../../../../types/bots";

import type {
    ValidatedCreateBotForm,
} from "../botForm";

export function buildCreateBotRequest(
    validatedForm: ValidatedCreateBotForm,
): CreateBotRequest {
    return {
        name:
            validatedForm.name,

        email:
            validatedForm.email,

        password:
            validatedForm.password,

        configuration: {
            marketplace:
                "VINTED",

            categoryPath:
                validatedForm
                    .category
                    .categoryPath,

            brand:
                validatedForm
                    .brand
                    .name,

            targetMode:
                validatedForm
                    .targetMode,

            model:
                validatedForm.model
                    ?.name
                ?? null,

            searchQuery:
                validatedForm
                    .searchQuery,

            minPrice:
                validatedForm.minPrice,

            maxPrice:
                validatedForm.maxPrice,

            autoRaiseOfferToVintedMinimum:
                validatedForm
                    .autoRaiseOfferToVintedMinimum,

            maxAutomaticOffer:
                validatedForm
                    .maxAutomaticOffer,

            dailyNegotiationBudget:
                validatedForm
                    .dailyNegotiationBudget,

            negotiationSteps:
                validatedForm
                    .negotiationSteps
                    .map(
                        (step) => ({
                            offerPrice:
                                step.offerPrice,

                            maxAcceptedCounterOffer:
                                step.maxAcceptedCounterOffer,

                            message:
                                step.message,
                        }),
                    ),
        },
    };
}
