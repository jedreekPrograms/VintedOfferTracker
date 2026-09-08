import {
    useEffect,
    useState,
} from "react";

interface BeforeInstallPromptEvent extends Event {
    prompt: () => Promise<void>;
    userChoice: Promise<{
        outcome: "accepted" | "dismissed";
        platform: string;
    }>;
}

function isStandaloneDisplayMode(): boolean {
    return window.matchMedia("(display-mode: standalone)").matches
        || (window.navigator as Navigator & { standalone?: boolean }).standalone === true;
}

function PwaInstallPrompt() {
    const [installEvent, setInstallEvent] = useState<BeforeInstallPromptEvent | null>(null);
    const [isInstalled, setIsInstalled] = useState(() => isStandaloneDisplayMode());

    useEffect(() => {
        const handleBeforeInstallPrompt = (event: Event) => {
            event.preventDefault();
            setInstallEvent(event as BeforeInstallPromptEvent);
        };

        const handleInstalled = () => {
            setInstallEvent(null);
            setIsInstalled(true);
        };

        window.addEventListener("beforeinstallprompt", handleBeforeInstallPrompt);
        window.addEventListener("appinstalled", handleInstalled);

        return () => {
            window.removeEventListener("beforeinstallprompt", handleBeforeInstallPrompt);
            window.removeEventListener("appinstalled", handleInstalled);
        };
    }, []);

    if (isInstalled || installEvent === null) {
        return null;
    }

    const install = async () => {
        await installEvent.prompt();
        const choice = await installEvent.userChoice;

        if (choice.outcome === "accepted") {
            setInstallEvent(null);
        }
    };

    return (
        <button
            className="mobile-install-button"
            type="button"
            onClick={() => {
                void install();
            }}
        >
            <span className="mobile-install-button-icon" aria-hidden="true">
                ↓
            </span>
            Zainstaluj aplikację
        </button>
    );
}

export default PwaInstallPrompt;
