import {
    useEffect,
    useState,
} from "react";

import {
    NavLink,
    Outlet,
    useLocation,
} from "react-router-dom";

import PwaInstallPrompt from "../components/PwaInstallPrompt";

interface NavigationItem {
    label: string;
    shortLabel?: string;
    path: string;
    end?: boolean;
    icon: NavigationIcon;
    mobilePrimary?: boolean;
}

type NavigationIcon =
    | "dashboard"
    | "runtime"
    | "bots"
    | "create"
    | "offers"
    | "history"
    | "pricing"
    | "dictionary"
    | "settings";

const navigationItems: NavigationItem[] = [
    {
        label: "Dashboard",
        path: "/",
        end: true,
        icon: "dashboard",
        mobilePrimary: true,
    },
    {
        label: "Runtime",
        path: "/runtime",
        icon: "runtime",
        mobilePrimary: true,
    },
    {
        label: "Boty",
        path: "/bots",
        icon: "bots",
        mobilePrimary: true,
    },
    {
        label: "Utwórz bota",
        shortLabel: "Nowy bot",
        path: "/bots/create",
        icon: "create",
    },
    {
        label: "Oferty do kupienia",
        shortLabel: "Oferty",
        path: "/action-required",
        icon: "offers",
        mobilePrimary: true,
    },
    {
        label: "Historia",
        path: "/history",
        icon: "history",
    },
    {
        label: "Cennik modeli",
        shortLabel: "Cennik",
        path: "/pricing",
        icon: "pricing",
    },
    {
        label: "Słowniki",
        path: "/dictionaries",
        end: true,
        icon: "dictionary",
    },
    {
        label: "Zarządzaj słownikami",
        shortLabel: "Ustawienia",
        path: "/dictionaries/manage",
        icon: "settings",
    },
];

const mobilePrimaryItems = navigationItems.filter(item => item.mobilePrimary);

function AppLayout() {
    const [isNavigationOpen, setIsNavigationOpen] = useState(false);
    const location = useLocation();

    useEffect(() => {
        setIsNavigationOpen(false);
    }, [location.pathname]);

    useEffect(() => {
        document.body.classList.toggle("mobile-navigation-open", isNavigationOpen);

        return () => {
            document.body.classList.remove("mobile-navigation-open");
        };
    }, [isNavigationOpen]);

    return (
        <div className="app-layout">
            <aside
                className={`sidebar ${isNavigationOpen ? "sidebar-open" : ""}`.trim()}
            >
                <div className="sidebar-topbar">
                    <div className="sidebar-header">
                        <div className="sidebar-logo">
                            F
                        </div>

                        <div>
                            <div className="sidebar-title">
                                FlipBot
                            </div>

                            <div className="sidebar-subtitle">
                                Panel zarządzania
                            </div>
                        </div>
                    </div>

                    <button
                        className="mobile-navigation-toggle"
                        type="button"
                        aria-label={isNavigationOpen
                            ? "Zamknij nawigację"
                            : "Otwórz nawigację"}
                        aria-expanded={isNavigationOpen}
                        onClick={() => setIsNavigationOpen(current => !current)}
                    >
                        <span
                            className="mobile-navigation-icon"
                            aria-hidden="true"
                        />
                    </button>
                </div>

                <nav
                    className="sidebar-navigation"
                    aria-label="Główna nawigacja"
                >
                    {navigationItems.map(item => (
                        <NavLink
                            key={item.path}
                            to={item.path}
                            end={item.end}
                            className={({ isActive }) =>
                                isActive
                                    ? "navigation-link navigation-link-active"
                                    : "navigation-link"
                            }
                        >
                            <NavIcon name={item.icon} />
                            <span>{item.label}</span>
                        </NavLink>
                    ))}
                </nav>

                <div className="sidebar-mobile-install">
                    <PwaInstallPrompt />
                </div>

                <div className="sidebar-footer">
                    <div className="sidebar-footer-title">
                        Zasada systemu
                    </div>

                    <div className="sidebar-footer-text">
                        Jedno konto Vinted jest przypisane do jednego bota.
                    </div>
                </div>
            </aside>

            {isNavigationOpen && (
                <button
                    className="mobile-navigation-backdrop"
                    type="button"
                    aria-label="Zamknij nawigację"
                    onClick={() => setIsNavigationOpen(false)}
                />
            )}

            <main className="main-content">
                <div className="mobile-app-header">
                    <div className="mobile-app-brand">
                        <span className="mobile-app-logo">F</span>
                        <div>
                            <div className="mobile-app-title">FlipBot</div>
                            <div className="mobile-app-subtitle">Mobile control</div>
                        </div>
                    </div>

                    <button
                        className="mobile-app-menu-button"
                        type="button"
                        aria-label="Otwórz pełne menu"
                        aria-expanded={isNavigationOpen}
                        onClick={() => setIsNavigationOpen(true)}
                    >
                        <span aria-hidden="true">•••</span>
                    </button>
                </div>

                <Outlet />
            </main>

            <nav
                className="mobile-bottom-navigation"
                aria-label="Mobilna nawigacja"
            >
                {mobilePrimaryItems.map(item => (
                    <NavLink
                        key={item.path}
                        to={item.path}
                        end={item.end}
                        className={({ isActive }) =>
                            isActive
                                ? "mobile-bottom-link mobile-bottom-link-active"
                                : "mobile-bottom-link"
                        }
                    >
                        <NavIcon name={item.icon} />
                        <span>{item.shortLabel ?? item.label}</span>
                    </NavLink>
                ))}

                <button
                    className={`mobile-bottom-link mobile-bottom-button ${isNavigationOpen ? "mobile-bottom-link-active" : ""}`.trim()}
                    type="button"
                    aria-label="Więcej"
                    onClick={() => setIsNavigationOpen(true)}
                >
                    <NavIcon name="settings" />
                    <span>Więcej</span>
                </button>
            </nav>
        </div>
    );
}

function NavIcon({ name }: { name: NavigationIcon }) {
    const common = {
        width: 20,
        height: 20,
        viewBox: "0 0 24 24",
        fill: "none",
        stroke: "currentColor",
        strokeWidth: 1.8,
        strokeLinecap: "round" as const,
        strokeLinejoin: "round" as const,
        "aria-hidden": true,
    };

    if (name === "dashboard") {
        return (
            <svg {...common} className="navigation-icon-svg">
                <rect x="3" y="3" width="7" height="7" rx="2" />
                <rect x="14" y="3" width="7" height="7" rx="2" />
                <rect x="3" y="14" width="7" height="7" rx="2" />
                <rect x="14" y="14" width="7" height="7" rx="2" />
            </svg>
        );
    }

    if (name === "runtime") {
        return (
            <svg {...common} className="navigation-icon-svg">
                <path d="M4 13h3l2-7 4 13 2-6h5" />
            </svg>
        );
    }

    if (name === "bots") {
        return (
            <svg {...common} className="navigation-icon-svg">
                <rect x="4" y="6" width="16" height="13" rx="4" />
                <path d="M9 2h6M12 2v4M8 12h.01M16 12h.01M9 16h6" />
            </svg>
        );
    }

    if (name === "create") {
        return (
            <svg {...common} className="navigation-icon-svg">
                <circle cx="12" cy="12" r="9" />
                <path d="M12 8v8M8 12h8" />
            </svg>
        );
    }

    if (name === "offers") {
        return (
            <svg {...common} className="navigation-icon-svg">
                <path d="M4 5h16v12H7l-3 3z" />
                <path d="M8 9h8M8 13h5" />
            </svg>
        );
    }

    if (name === "history") {
        return (
            <svg {...common} className="navigation-icon-svg">
                <path d="M4 12a8 8 0 1 0 2.3-5.7L4 8.6" />
                <path d="M4 4v4.6h4.6M12 8v4l3 2" />
            </svg>
        );
    }

    if (name === "pricing") {
        return (
            <svg {...common} className="navigation-icon-svg">
                <path d="M5 5h9l5 5-9 9-5-5z" />
                <circle cx="10" cy="10" r="1" />
            </svg>
        );
    }

    if (name === "dictionary") {
        return (
            <svg {...common} className="navigation-icon-svg">
                <path d="M4 5a3 3 0 0 1 3-2h5v17H7a3 3 0 0 0-3 2z" />
                <path d="M20 5a3 3 0 0 0-3-2h-5v17h5a3 3 0 0 1 3 2z" />
            </svg>
        );
    }

    return (
        <svg {...common} className="navigation-icon-svg">
            <circle cx="12" cy="12" r="3" />
            <path d="M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1-2.8 2.8-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.6v.2h-4V21a1.7 1.7 0 0 0-1-1.6 1.7 1.7 0 0 0-1.9.3l-.1.1L4.2 17l.1-.1a1.7 1.7 0 0 0 .3-1.9A1.7 1.7 0 0 0 3 14H2.8v-4H3a1.7 1.7 0 0 0 1.6-1 1.7 1.7 0 0 0-.3-1.9L4.2 7 7 4.2l.1.1a1.7 1.7 0 0 0 1.9.3 1.7 1.7 0 0 0 1-1.6v-.2h4V3a1.7 1.7 0 0 0 1 1.6 1.7 1.7 0 0 0 1.9-.3l.1-.1L19.8 7l-.1.1a1.7 1.7 0 0 0-.3 1.9 1.7 1.7 0 0 0 1.6 1h.2v4H21a1.7 1.7 0 0 0-1.6 1z" />
        </svg>
    );
}

export default AppLayout;
