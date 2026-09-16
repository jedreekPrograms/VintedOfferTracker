export interface VisiblePoller {
    refresh: () => Promise<void>;
    stop: () => void;
}

/** One request at a time, with a fresh request after an in-flight manual refresh. */
export function createVisiblePoller(
    task: (signal: AbortSignal) => Promise<void>,
    intervalMs: number,
): VisiblePoller {
    let stopped = false;
    let timer: ReturnType<typeof setTimeout> | undefined;
    let running: Promise<void> | null = null;
    let followUp: Promise<void> | null = null;
    let controller: AbortController | null = null;

    function clearTimer() {
        clearTimeout(timer);
        timer = undefined;
    }

    function refresh(): Promise<void> {
        if (stopped || document.visibilityState === "hidden") {
            return Promise.resolve();
        }
        clearTimer();

        if (running !== null) {
            followUp ??= running.catch(() => {}).then(() => {
                followUp = null;
                return refresh();
            });
            return followUp;
        }

        const requestController = new AbortController();
        controller = requestController;
        running = Promise.resolve().then(() => {
            if (!requestController.signal.aborted) {
                return task(requestController.signal);
            }
        });
        void running.finally(() => {
            running = null;
            controller = null;
            if (!stopped && document.visibilityState !== "hidden" && followUp === null) {
                timer = setTimeout(trigger, intervalMs);
            }
        }).catch(() => {});
        return running;
    }

    function trigger() {
        // Tasks display their own errors. A failed poll must not stop future polls.
        void refresh().catch(() => {});
    }

    function visibilityChanged() {
        clearTimer();
        if (document.visibilityState === "hidden") {
            controller?.abort();
        } else {
            trigger();
        }
    }

    document.addEventListener("visibilitychange", visibilityChanged);
    trigger();

    return {
        refresh,
        stop() {
            stopped = true;
            clearTimer();
            controller?.abort();
            document.removeEventListener("visibilitychange", visibilityChanged);
        },
    };
}
