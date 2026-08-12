import {
    useRef,
    useState,
} from "react";

import type {
    TargetMode,
} from "../../../../types/bots";

import type {
    CreateBotFormValues,
    NegotiationStepField,
    NegotiationStepForm,
} from "../botForm";

const MAX_NEGOTIATION_STEPS = 25;

function createInitialForm(): CreateBotFormValues {
    return {
        botName: "",

        email: "",
        password: "",

        selectedCategoryId: "",
        selectedBrandId: "",

        targetMode:
            "VINTED_MODEL",

        selectedModelId: "",

        searchQuery: "",

        minPrice: "",
        maxPrice: "",

        autoRaiseOfferToVintedMinimum:
            false,

        maxAutomaticOffer: "",

        dailyNegotiationBudget:
            "25",

        negotiationSteps: [
            {
                id: 1,
                offerPrice: "",
                maxAcceptedCounterOffer: "",
                message: "",
            },
        ],
    };
}

export function useCreateBotForm() {
    const [
        form,
        setForm,
    ] = useState<CreateBotFormValues>(
        createInitialForm,
    );

    const nextNegotiationStepId =
        useRef(2);

    function setBotName(
        value: string,
    ) {
        setForm(
            (currentForm) => ({
                ...currentForm,
                botName: value,
            }),
        );
    }

    function setEmail(
        value: string,
    ) {
        setForm(
            (currentForm) => ({
                ...currentForm,
                email: value,
            }),
        );
    }

    function setPassword(
        value: string,
    ) {
        setForm(
            (currentForm) => ({
                ...currentForm,
                password: value,
            }),
        );
    }

    function setCategory(
        categoryId: string,
    ) {
        setForm(
            (currentForm) => ({
                ...currentForm,
                selectedCategoryId:
                    categoryId,
            }),
        );
    }

    function setBrand(
        brandId: string,
    ) {
        setForm(
            (currentForm) => ({
                ...currentForm,

                selectedBrandId:
                    brandId,

                /*
                 * Poprzednio wybrany model należał
                 * do poprzedniej marki.
                 */
                selectedModelId:
                    "",
            }),
        );
    }

    function setTargetMode(
        targetMode: TargetMode,
    ) {
        setForm(
            (currentForm) => ({
                ...currentForm,
                targetMode,
            }),
        );
    }

    function setModel(
        modelId: string,
    ) {
        setForm(
            (currentForm) => ({
                ...currentForm,
                selectedModelId:
                    modelId,
            }),
        );
    }

    function setSearchQuery(
        value: string,
    ) {
        setForm(
            (currentForm) => ({
                ...currentForm,
                searchQuery:
                    value,
            }),
        );
    }

    function setMinPrice(
        value: string,
    ) {
        setForm(
            (currentForm) => ({
                ...currentForm,
                minPrice: value,
            }),
        );
    }

    function setMaxPrice(
        value: string,
    ) {
        setForm(
            (currentForm) => ({
                ...currentForm,
                maxPrice: value,
            }),
        );
    }

    function setAutoRaiseOfferToVintedMinimum(
        value: boolean,
    ) {
        setForm(
            (currentForm) => ({
                ...currentForm,
                autoRaiseOfferToVintedMinimum:
                    value,
            }),
        );
    }

    function setMaxAutomaticOffer(
        value: string,
    ) {
        setForm(
            (currentForm) => ({
                ...currentForm,
                maxAutomaticOffer:
                    value,
            }),
        );
    }

    function setDailyNegotiationBudget(
        value: string,
    ) {
        setForm(
            (currentForm) => ({
                ...currentForm,
                dailyNegotiationBudget:
                    value,
            }),
        );
    }

    function addNegotiationStep(): boolean {
        if (
            form.negotiationSteps.length
            >= MAX_NEGOTIATION_STEPS
        ) {
            return false;
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

        setForm(
            (currentForm) => ({
                ...currentForm,

                negotiationSteps: [
                    ...currentForm.negotiationSteps,
                    newStep,
                ],
            }),
        );

        return true;
    }

    function removeNegotiationStep(
        stepId: number,
    ): boolean {
        if (
            form.negotiationSteps.length
            <= 1
        ) {
            return false;
        }

        setForm(
            (currentForm) => ({
                ...currentForm,

                negotiationSteps:
                    currentForm.negotiationSteps.filter(
                        (step) =>
                            step.id
                            !== stepId,
                    ),
            }),
        );

        return true;
    }

    function updateNegotiationStep(
        stepId: number,
        field: NegotiationStepField,
        value: string,
    ) {
        setForm(
            (currentForm) => ({
                ...currentForm,

                negotiationSteps:
                    currentForm.negotiationSteps.map(
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
            }),
        );
    }

    function resetForm() {
        setForm(
            createInitialForm(),
        );

        nextNegotiationStepId.current =
            2;
    }

    return {
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

        resetForm,
    };
}
