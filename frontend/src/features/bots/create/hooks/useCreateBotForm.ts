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
const RESEARCH_PRESET_STEP_COUNT = 5;
const CONCESSION_DECAY = 0.70;

const RESEARCH_MESSAGES = [
    "Cześć, czy taka kwota byłaby okej?",
    "Mogę trochę podnieść ofertę.",
    "Mogę jeszcze trochę podejść z ceną.",
    "Z mojej strony zostało już niewiele pola do ruchu.",
    "To już mój maks. Jeśli pasuje, możemy zamknąć temat.",
] as const;

function researchRejectionWaitHours(stepNumber: number): string {
    if (stepNumber === 1) return "1";
    if (stepNumber === 2) return "2";
    if (stepNumber === 3) return "4";
    return "8";
}

function createInitialRules(): NegotiationStepForm["counterOfferRules"] {
    return [
        {
            id: 1,
            minimumDiscountPercent: "5",
            action: "WAIT_BEFORE_NEXT_STEP",
            waitHours: "2",
        },
        {
            id: 2,
            minimumDiscountPercent: "10",
            action: "WAIT_BEFORE_NEXT_STEP",
            waitHours: "1",
        },
        {
            id: 3,
            minimumDiscountPercent: "15",
            action: "NEXT_STEP_NOW",
            waitHours: "",
        },
    ];
}

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
                message: RESEARCH_MESSAGES[0],
                rejectionAction: "WAIT_BEFORE_NEXT_STEP",
                rejectionWaitHours: "1",
                counterOfferDefaultAction: "WAIT_BEFORE_NEXT_STEP",
                counterOfferDefaultWaitHours: "3",
                counterOfferRules: createInitialRules(),
            },
        ],
    };
}

function defaultRejectionPolicy(stepNumber: number): {
    action: NegotiationReactionAction;
    waitHours: string;
} {
    return {
        action: "WAIT_BEFORE_NEXT_STEP",
        waitHours: researchRejectionWaitHours(stepNumber),
    };
}

export function buildDecreasingPriceLadder(
    firstOffer: number,
    cap: number,
    stepCount = RESEARCH_PRESET_STEP_COUNT,
): number[] | null {
    if (!Number.isFinite(firstOffer)
        || !Number.isFinite(cap)
        || firstOffer <= 0
        || cap <= firstOffer
        || stepCount < 2
        || cap - firstOffer < (stepCount - 1) * 10) {
        return null;
    }

    const prices = [firstOffer];
    let current = firstOffer;

    for (let stepIndex = 1; stepIndex < stepCount; stepIndex += 1) {
        const remainingTransitions = stepCount - stepIndex;

        if (remainingTransitions === 1) {
            prices.push(cap);
            break;
        }

        const gap = cap - current;
        let weightSum = 0;
        let weight = 1;
        for (let index = 0; index < remainingTransitions; index += 1) {
            weightSum += weight;
            weight *= CONCESSION_DECAY;
        }

        const rawConcession = gap / weightSum;
        let concession = Math.ceil(rawConcession / 10) * 10;
        concession = Math.max(10, concession);

        // Leave at least 10 PLN for every later move.
        const maxNow = cap - ((remainingTransitions - 1) * 10);
        const next = Math.min(maxNow, current + concession);

        if (next <= current) {
            return null;
        }

        prices.push(next);
        current = next;
    }

    return prices.length === stepCount ? prices : null;
}

export function useCreateBotForm() {
    const [form, setForm] = useState<CreateBotFormValues>(createInitialForm);
    const nextNegotiationStepId = useRef(2);
    const nextCounterRuleId = useRef(4);

    function updateFormField<K extends keyof CreateBotFormValues>(
        field: K,
        value: CreateBotFormValues[K],
    ) {
        setForm((currentForm) => ({
            ...currentForm,
            [field]: value,
        }));
    }

    function setBotName(value: string) { updateFormField("botName", value); }
    function setEmail(value: string) { updateFormField("email", value); }
    function setPassword(value: string) { updateFormField("password", value); }
    function setCategory(categoryId: string) { updateFormField("selectedCategoryId", categoryId); }

    function setBrand(brandId: string) {
        setForm((currentForm) => ({
            ...currentForm,
            selectedBrandId: brandId,
            selectedModelId: "",
        }));
    }

    function setTargetMode(targetMode: TargetMode) { updateFormField("targetMode", targetMode); }
    function setModel(modelId: string) { updateFormField("selectedModelId", modelId); }
    function setSearchQuery(value: string) { updateFormField("searchQuery", value); }
    function setMinPrice(value: string) { updateFormField("minPrice", value); }
    function setMaxPrice(value: string) { updateFormField("maxPrice", value); }
    function setAutoRaiseOfferToVintedMinimum(value: boolean) {
        updateFormField("autoRaiseOfferToVintedMinimum", value);
    }
    function setMaxAutomaticOffer(value: string) { updateFormField("maxAutomaticOffer", value); }
    function setDailyNegotiationBudget(value: string) { updateFormField("dailyNegotiationBudget", value); }

    function createDefaultRules() {
        const fivePercentId = nextCounterRuleId.current++;
        const tenPercentId = nextCounterRuleId.current++;
        const fifteenPercentId = nextCounterRuleId.current++;
        return [
            {
                id: fivePercentId,
                minimumDiscountPercent: "5",
                action: "WAIT_BEFORE_NEXT_STEP" as NegotiationReactionAction,
                waitHours: "2",
            },
            {
                id: tenPercentId,
                minimumDiscountPercent: "10",
                action: "WAIT_BEFORE_NEXT_STEP" as NegotiationReactionAction,
                waitHours: "1",
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
        if (form.negotiationSteps.length >= MAX_NEGOTIATION_STEPS) return false;

        const stepNumber = form.negotiationSteps.length + 1;
        const rejection = defaultRejectionPolicy(stepNumber);
        const newStep: NegotiationStepForm = {
            id: nextNegotiationStepId.current++,
            offerPrice: "",
            maxAcceptedCounterOffer: "",
            message: RESEARCH_MESSAGES[Math.min(stepNumber - 1, RESEARCH_MESSAGES.length - 1)],
            rejectionAction: rejection.action,
            rejectionWaitHours: rejection.waitHours,
            counterOfferDefaultAction: "WAIT_BEFORE_NEXT_STEP",
            counterOfferDefaultWaitHours: "3",
            counterOfferRules: createDefaultRules(),
        };

        setForm((currentForm) => ({
            ...currentForm,
            negotiationSteps: [...currentForm.negotiationSteps, newStep],
        }));
        return true;
    }

    function applyResearchNegotiationPreset(): boolean {
        const firstOffer = Number(form.negotiationSteps[0]?.offerPrice);
        const cap = Number(form.maxAutomaticOffer);
        const prices = buildDecreasingPriceLadder(firstOffer, cap);
        if (prices === null) return false;

        const newSteps: NegotiationStepForm[] = prices.map((price, index) => {
            const stepNumber = index + 1;
            const existingId = form.negotiationSteps[index]?.id
                ?? nextNegotiationStepId.current++;
            const nextPrice = prices[Math.min(index + 1, prices.length - 1)];

            return {
                id: existingId,
                offerPrice: String(price),
                maxAcceptedCounterOffer: String(nextPrice),
                message: RESEARCH_MESSAGES[index],
                rejectionAction: "WAIT_BEFORE_NEXT_STEP",
                rejectionWaitHours: researchRejectionWaitHours(stepNumber),
                counterOfferDefaultAction: "WAIT_BEFORE_NEXT_STEP",
                counterOfferDefaultWaitHours: "3",
                counterOfferRules: createDefaultRules(),
            };
        });

        setForm((currentForm) => ({
            ...currentForm,
            autoRaiseOfferToVintedMinimum: true,
            negotiationSteps: newSteps,
        }));
        return true;
    }

    function removeNegotiationStep(stepId: number): boolean {
        if (form.negotiationSteps.length <= 1) return false;
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
                step.id === stepId ? { ...step, [field]: value } : step
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
                if (step.id !== stepId) return step;

                if (field === "rejectionAction") {
                    const action = value as NegotiationReactionAction;
                    return {
                        ...step,
                        rejectionAction: action,
                        rejectionWaitHours: action === "NEXT_STEP_NOW"
                            ? ""
                            : step.rejectionWaitHours || "1",
                    };
                }

                if (field === "counterOfferDefaultAction") {
                    const action = value as NegotiationReactionAction;
                    return {
                        ...step,
                        counterOfferDefaultAction: action,
                        counterOfferDefaultWaitHours: action === "NEXT_STEP_NOW"
                            ? ""
                            : step.counterOfferDefaultWaitHours || "3",
                    };
                }

                return { ...step, [field]: value };
            }),
        }));
    }

    function addCounterOfferRule(stepId: number): boolean {
        const step = form.negotiationSteps.find((candidate) => candidate.id === stepId);
        if (step === undefined
            || step.counterOfferRules.length >= MAX_COUNTER_RULES_PER_STEP) return false;

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
                    ? { ...candidate, counterOfferRules: [...candidate.counterOfferRules, newRule] }
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
                if (step.id !== stepId) return step;
                return {
                    ...step,
                    counterOfferRules: step.counterOfferRules.map((rule) => {
                        if (rule.id !== ruleId) return rule;
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
                        return { ...rule, [field]: value };
                    }),
                };
            }),
        }));
    }

    function replaceForm(values: CreateBotFormValues) {
        setForm(values);
        const highestStepId = values.negotiationSteps.reduce(
            (highestId, step) => Math.max(highestId, step.id), 0,
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
        nextCounterRuleId.current = 4;
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
        applyResearchNegotiationPreset,
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
