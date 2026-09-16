import { useCallback, useEffect, useRef } from "react";
import { createVisiblePoller, type VisiblePoller } from "../utils/visiblePolling";

export function useVisiblePolling(
    task: (signal: AbortSignal) => Promise<void>,
    intervalMs: number,
): () => Promise<void> {
    const pollerRef = useRef<VisiblePoller | null>(null);

    useEffect(() => {
        const poller = createVisiblePoller(task, intervalMs);
        pollerRef.current = poller;
        return () => {
            poller.stop();
            pollerRef.current = null;
        };
    }, [task, intervalMs]);

    return useCallback(() => pollerRef.current?.refresh() ?? Promise.resolve(), []);
}
