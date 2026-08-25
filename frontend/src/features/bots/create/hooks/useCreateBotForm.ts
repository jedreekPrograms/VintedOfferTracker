import {
    useRef,
    useState,
} from "react";

import type {
    NegotiationReactionAction,
    TargetMode,
} from "../../../../types/bots";

import type {
    CounterOfferRuleField,
    CreateBotFormValues,
    NegotiationStepField,
    NegotiationStepForm,
    NegotiationStepPolicyField,
} from "../botForm";

const MAX_NEGOTIATION_STEPS = 25;
const MAX_COUNTER_RULES_PER_STEP = 25;

function createInitialForm(): CreateBotFormValues {
    return {
        botName: "",
        email: "",
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
        dailyNegotiationBudget: "25",
        negotiationSteps: [
            {
                id: 1,
                offerPrice: "",
                maxAcceptedCounterOffer: "",
                message: "",
                rejectionAction: "NEXT_STEP_NOW",
                rejectionWaitHours: "",
                readWaitHours: "3",
                unreadWaitHours: "48",
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
            },
        ],
    };
}

function defaultRejectionPolicy(stepNumber: number): {
    action: NegotiationReactionAction;
    waitHours: string;
} {
    if (stepNumber === 1) {
        return {
            action: "NEXT_STEP_NOW",
            waitHours: "",
        };
    }

    if (stepNumber === 2) {
        return {
            action: "WAIT_BEFORE_NEXT_STEP",
            waitHours: "6",
        };
    }

    if (stepNumber === 3) {
        return {
            action: "WAIT_BEFORE_NEXT_STEP",
            waitHours: "12",
        };
    }

    return {
        action: "WAIT_BEFORE_NEXT_STEP",
        waitHours: "24",
    };
}

export function useCreateBotForm() {
    const [form, setForm] = useState<CreateBotFormValues>(createInitialForm);
    const nextNegotiationStepId = useRef(2);
    const nextCounterRuleId = useRef(3);

    function updateFormField<K extends keyof CreateBotFormValues>(
        field: K,
        value: CreateBotFormValues[K],
    ) {
        setForm((currentForm) => ({
            ...currentForm,
            [field]: value,
        }));
    }

    function setBotName(value: string) {
        updateFormField("botName", value);
    }

    function setEmail(value: string) {
        updateFormField("email", value);
    }

    function setPassword(value: string) {
        updateFormField("password", value);
    }

    function setCategory(categoryId: string) {
        updateFormField("selectedCategoryId", categoryId);
    }

    function setBrand(brandId: string) {
        setForm((currentForm) => ({
            ...currentForm,
            selectedBrandId: brandId,
            selectedModelId: "",
        }));
    }

    function setTargetMode(targetMode: TargetMode) {
        updateFormField("targetMode", targetMode);
    }

    function setModel(modelId: string) {
        updateFormField("selectedModelId", modelId);
    }

    function setSearchQuery(value: string) {
        updateFormField("searchQuery", value);
    }

    function setMinPrice(value: string) {
        updateFormField("minPrice", value);
    }

    function setMaxPrice(value: string) {
        updateFormField("maxPrice", value);
    }

    function setAutoRaiseOfferToVintedMinimum(value: boolean) {
        updateFormField("autoRaiseOfferToVintedMinimum", value);
    }

    function setMaxAutomaticOffer(value: string) {
        updateFormField("maxAutomaticOffer", value);
    }

    function setDailyNegotiationBudget(value: string) {
        updateFormField("dailyNegotiationBudget", value);
    }

    function createDefaultRules() {
        const tenPercentId = nextCounterRuleId.current++;
        const fifteenPercentId = nextCounterRuleId.current++;

        return [
            {
                id: tenPercentId,
                minimumDiscountPercent: "10",
                action: "WAIT_BEFORE_NEXT_STEP" as NegotiationReactionAction,
                waitHours: "2",
            },
            {
                id: fifteenPercentId,
                minimumDiscountPercent: "15",
                action: "NEXT_STEP_NOW" as NegotiationReactionAction,
                waitHours: "",
            },
        ];
    }

    function addNegotiationStep(): boolean {
        if (form.negotiationSteps.length >= MAX_NEGOTIATION_STEPS) {
            return false;
        }

        const stepNumber = form.negotiationSteps.length + 1;
        const rejection = defaultRejectionPolicy(stepNumber);

        const newStep: NegotiationStepForm = {
            id: nextNegotiationStepId.current++,
            offerPrice: "",
            maxAcceptedCounterOffer: "",
            message: "",
            rejectionAction: rejection.action,
            rejectionWaitHours: rejection.waitHours,
            readWaitHours: "3",
            unreadWaitHours: "48",
            counterOfferDefaultAction: "WAIT_BEFORE_NEXT_STEP",
            counterOfferDefaultWaitHours: "6",
            counterOfferRules: createDefaultRules(),
        };

        setForm((currentForm) => ({
            ...currentForm,
            negotiationSteps: [
                ...currentForm.negotiationSteps,
                newStep,
            ],
        }));
        return true;
    }

    function removeNegotiationStep(stepId: number): boolean {
        if (form.negotiationSteps.length <= 1) {
            return false;
        }

        setForm((currentForm) => ({
            ...currentForm,
            negotiationSteps: currentForm.negotiationSteps.filter(
                (step) => step.id !== stepId,
            ),
        }));
        return true;
    }

    function updateNegotiationStep(
        stepId: number,
        field: NegotiationStepField,
        value: string,
    ) {
        setForm((currentForm) => ({
            ...currentForm,
            negotiationSteps: currentForm.negotiationSteps.map((step) =>
                step.id === stepId
                    ? {
                        ...step,
                        [field]: value,
                    }
                    : step
            ),
        }));
    }

    function updateNegotiationStepPolicy(
        stepId: number,
        field: NegotiationStepPolicyField,
        value: string,
    ) {
        setForm((currentForm) => ({
            ...currentForm,
            negotiationSteps: currentForm.negotiationSteps.map((step) => {
                if (step.id !== stepId) {
                    return step;
                }

                if (field === "rejectionAction") {
                    const action = value as NegotiationReactionAction;
                    return {
                        ...step,
                        rejectionAction: action,
                        rejectionWaitHours: action === "NEXT_STEP_NOW"
                            ? ""
                            : step.rejectionWaitHours || "6",
                    };
                }

                if (field === "counterOfferDefaultAction") {
                    const action = value as NegotiationReactionAction;
                    return {
                        ...step,
                        counterOfferDefaultAction: action,
                        counterOfferDefaultWaitHours: action === "NEXT_STEP_NOW"
                            ? ""
                            : step.counterOfferDefaultWaitHours || "6",
                    };
                }

                return {
                    ...step,
                    [field]: value,
                };
            }),
        }));
    }

    function addCounterOfferRule(stepId: number): boolean {
        const step = form.negotiationSteps.find((candidate) => candidate.id === stepId);
        if (step === undefined
            || step.counterOfferRules.length >= MAX_COUNTER_RULES_PER_STEP) {
            return false;
        }

        const newRule = {
            id: nextCounterRuleId.current++,
            minimumDiscountPercent: "",
            action: "WAIT_BEFORE_NEXT_STEP" as NegotiationReactionAction,
            waitHours: "2",
        };

        setForm((currentForm) => ({
            ...currentForm,
            negotiationSteps: currentForm.negotiationSteps.map((candidate) =>
                candidate.id === stepId
                    ? {
                        ...candidate,
                        counterOfferRules: [
                            ...candidate.counterOfferRules,
                            newRule,
                        ],
                    }
                    : candidate
            ),
        }));
        return true;
    }

    function removeCounterOfferRule(stepId: number, ruleId: number) {
        setForm((currentForm) => ({
            ...currentForm,
            negotiationSteps: currentForm.negotiationSteps.map((step) =>
                step.id === stepId
                    ? {
                        ...step,
                        counterOfferRules: step.counterOfferRules.filter(
                            (rule) => rule.id !== ruleId,
                        ),
                    }
                    : step
            ),
        }));
    }

    function updateCounterOfferRule(
        stepId: number,
        ruleId: number,
        field: CounterOfferRuleField,
        value: string,
    ) {
        setForm((currentForm) => ({
            ...currentForm,
            negotiationSteps: currentForm.negotiationSteps.map((step) => {
                if (step.id !== stepId) {
                    return step;
                }

                return {
                    ...step,
                    counterOfferRules: step.counterOfferRules.map((rule) => {
                        if (rule.id !== ruleId) {
                            return rule;
                        }

                        if (field === "action") {
                            const action = value as NegotiationReactionAction;
                            return {
                                ...rule,
                                action,
                                waitHours: action === "NEXT_STEP_NOW"
                                    ? ""
                                    : rule.waitHours || "2",
                            };
                        }

                        return {
                            ...rule,
                            [field]: value,
                        };
                    }),
                };
            }),
        }));
    }

    function replaceForm(values: CreateBotFormValues) {
        setForm(values);

        const highestStepId = values.negotiationSteps.reduce(
            (highestId, step) => Math.max(highestId, step.id),
            0,
        );
        const highestRuleId = values.negotiationSteps.reduce(
            (highestId, step) => Math.max(
                highestId,
                ...step.counterOfferRules.map((rule) => rule.id),
            ),
            0,
        );

        nextNegotiationStepId.current = Math.max(highestStepId + 1, 1);
        nextCounterRuleId.current = Math.max(highestRuleId + 1, 1);
    }

    function resetForm() {
        setForm(createInitialForm());
        nextNegotiationStepId.current = 2;
        nextCounterRuleId.current = 3;
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
        updateNegotiationStepPolicy,
        addCounterOfferRule,
        removeCounterOfferRule,
        updateCounterOfferRule,
        replaceForm,
        resetForm,
    };
}
