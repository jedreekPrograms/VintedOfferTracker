package pl.flipbot.playwright.worker;

record WorkerSlotCapacityPlan(
        int startCount,
        int retireCount
) {

    static WorkerSlotCapacityPlan between(
            int availableSlots,
            int targetSlots
    ) {
        if (availableSlots < 0 || targetSlots < 0) {
            throw new IllegalArgumentException(
                    "Worker slot counts cannot be negative."
            );
        }

        if (availableSlots < targetSlots) {
            return new WorkerSlotCapacityPlan(
                    targetSlots - availableSlots,
                    0
            );
        }

        if (availableSlots > targetSlots) {
            return new WorkerSlotCapacityPlan(
                    0,
                    availableSlots - targetSlots
            );
        }

        return new WorkerSlotCapacityPlan(0, 0);
    }
}
