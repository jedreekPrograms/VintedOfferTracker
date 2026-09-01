import { useState } from "react";

import {
    NavLink,
    Outlet,
} from "react-router-dom";

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

interface NavigationItem {
    label: string;
    path: string;
    icon: NavigationIcon;
    end?: boolean;
}

interface NavigationGroup {
    label: string;
    items: NavigationItem[];
}

const navigationGroups: NavigationGroup[] = [
    {
        label: "Workspace",
        items: [
            {
                label: "Dashboard",
                path: "/",
                icon: "dashboard",
                end: true,
            },
            {
                label: "Runtime",
                path: "/runtime",
                icon: "runtime",
            },
            {
                label: "Boty",
                path: "/bots",
                icon: "bots",
            },
            {
                label: "Utwórz bota",
                path: "/bots/create",
                icon: "create",
            },
            {
                label: "Oferty do kupienia",
                path: "/action-required",
                icon: "offers",
            },
            {
                label: "Historia",
                path: "/history",
                icon: "history",
            },
        ],
    },
    {
        label: "Konfiguracja",
        items: [
            {
                label: "Cennik modeli",
                path: "/pricing",
                icon: "pricing",
            },
            {
                label: "Słowniki",
                path: "/dictionaries",
                icon: "dictionary",
                end: true,
            },
            {
                label: "Zarządzaj słownikami",
                path: "/dictionaries/manage",
                icon: "settings",
            },
        ],
    },
];

function AppLayout() {
    const [isNavigationOpen, setIsNavigationOpen] = useState(false);

    return (
        <div className="app-layout">
            <aside
                className={`sidebar ${isNavigationOpen ? "sidebar-open" : ""}`.trim()}
            >
                <div className="sidebar-ambient" aria-hidden="true" />

                <div className="sidebar-topbar">
                    <div className="sidebar-header">
                        <div className="sidebar-logo" aria-hidden="true">
                            <span className="sidebar-logo-mark">F</span>
                        </div>

                        <div className="sidebar-brand-copy">
                            <div className="sidebar-title">
                                FlipBot
                            </div>

                            <div className="sidebar-subtitle">
                                Control center
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
                    {navigationGroups.map(group => (
                        <div
                            className="navigation-group"
                            key={group.label}
                        >
                            <div className="navigation-group-label">
                                {group.label}
                            </div>

                            <div className="navigation-group-links">
                                {group.items.map(item => (
                                    <NavLink
                                        key={item.path}
                                        to={item.path}
                                        end={item.end}
                                        className={({ isActive }) =>
                                            isActive
                                                ? "navigation-link navigation-link-active"
                                                : "navigation-link"
                                        }
                                        onClick={() => setIsNavigationOpen(false)}
                                    >
                                        <span className="navigation-icon" aria-hidden="true">
                                            <NavigationGlyph icon={item.icon} />
                                        </span>

                                        <span className="navigation-label">
                                            {item.label}
                                        </span>

                                        <span className="navigation-active-indicator" aria-hidden="true" />
                                    </NavLink>
                                ))}
                            </div>
                        </div>
                    ))}
                </nav>

                <div className="sidebar-footer">
                    <div className="sidebar-footer-status-row">
                        <span className="sidebar-footer-status-dot" aria-hidden="true" />
                        <span>System lokalny</span>
                    </div>

                    <div className="sidebar-footer-title">
                        FlipBot workspace
                    </div>

                    <div className="sidebar-footer-text">
                        Jedno konto Vinted jest przypisane do jednego bota.
                    </div>
                </div>
            </aside>

            <main className="main-content">
                <div className="main-content-glow" aria-hidden="true" />
                <Outlet />
            </main>
        </div>
    );
}

function NavigationGlyph({ icon }: { icon: NavigationIcon }) {
    const commonProps = {
        width: 18,
        height: 18,
        viewBox: "0 0 24 24",
        fill: "none",
        stroke: "currentColor",
        strokeWidth: 1.8,
        strokeLinecap: "round" as const,
        strokeLinejoin: "round" as const,
    };

    switch (icon) {
        case "dashboard":
            return (
                <svg {...commonProps}>
                    <rect x="3" y="3" width="7" height="7" rx="2" />
                    <rect x="14" y="3" width="7" height="7" rx="2" />
                    <rect x="3" y="14" width="7" height="7" rx="2" />
                    <rect x="14" y="14" width="7" height="7" rx="2" />
                </svg>
            );
        case "runtime":
            return (
                <svg {...commonProps}>
                    <path d="M4 17l4-5 4 3 4-8 4 4" />
                    <path d="M4 21h16" />
                </svg>
            );
        case "bots":
            return (
                <svg {...commonProps}>
                    <rect x="4" y="7" width="16" height="12" rx="3" />
                    <path d="M9 11h.01M15 11h.01M8 15h8M12 7V4M9 4h6" />
                </svg>
            );
        case "create":
            return (
                <svg {...commonProps}>
                    <path d="M12 5v14M5 12h14" />
                </svg>
            );
        case "offers":
            return (
                <svg {...commonProps}>
                    <path d="M5 5h14v14H5z" />
                    <path d="M8 9h8M8 13h5M8 17h3" />
                </svg>
            );
        case "history":
            return (
                <svg {...commonProps}>
                    <path d="M3 12a9 9 0 1 0 3-6.7L3 8" />
                    <path d="M3 3v5h5M12 7v5l3 2" />
                </svg>
            );
        case "pricing":
            return (
                <svg {...commonProps}>
                    <path d="M4 7h16v10H4z" />
                    <path d="M8 10h4M8 14h7" />
                </svg>
            );
        case "dictionary":
            return (
                <svg {...commonProps}>
                    <path d="M5 4h11a3 3 0 0 1 3 3v13H8a3 3 0 0 1-3-3V4z" />
                    <path d="M8 4v16M11 8h5M11 12h5" />
                </svg>
            );
        case "settings":
            return (
                <svg {...commonProps}>
                    <circle cx="12" cy="12" r="3" />
                    <path d="M19 12a7 7 0 0 0-.1-1l2-1.5-2-3.4-2.4 1a8 8 0 0 0-1.7-1L14.5 3h-5l-.4 3.1a8 8 0 0 0-1.7 1l-2.4-1-2 3.4L5.1 11a7 7 0 0 0 0 2L3 14.5l2 3.4 2.4-1a8 8 0 0 0 1.7 1l.4 3.1h5l.4-3.1a8 8 0 0 0 1.7-1l2.4 1 2-3.4-2.1-1.5a7 7 0 0 0 .1-1z" />
                </svg>
            );
    }
}

export default AppLayout;
