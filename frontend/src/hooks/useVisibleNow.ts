import { useEffect, useState } from "react";

export function useVisibleNow(intervalMs: number, enabled = true): number {
    const [now, setNow] = useState(() => Date.now());

    useEffect(() => {
        if (!enabled) {
            return;
        }
        let timer: ReturnType<typeof setInterval> | undefined;
        function restart() {
            clearInterval(timer);
            if (document.visibilityState !== "hidden") {
                setNow(Date.now());
                timer = setInterval(() => setNow(Date.now()), intervalMs);
            }
        }
        document.addEventListener("visibilitychange", restart);
        restart();
        return () => {
            clearInterval(timer);
            document.removeEventListener("visibilitychange", restart);
        };
    }, [intervalMs, enabled]);

    return now;
}
