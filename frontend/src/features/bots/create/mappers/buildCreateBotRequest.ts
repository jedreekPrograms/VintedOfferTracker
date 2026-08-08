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

            model:
                validatedForm
                    .model
                    .name,

            minPrice:
                validatedForm.minPrice,

            maxPrice:
                validatedForm.maxPrice,

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