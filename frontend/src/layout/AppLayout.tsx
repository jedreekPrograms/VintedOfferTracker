import { useState } from "react";

import {
    NavLink,
    Outlet,
} from "react-router-dom";

interface NavigationItem {
    label: string;
    path: string;
    end?: boolean;
}

const navigationItems: NavigationItem[] = [
    {
        label: "Dashboard",
        path: "/",
        end: true,
    },
    {
        label: "Runtime",
        path: "/runtime",
    },
    {
        label: "Boty",
        path: "/bots",
    },
    {
        label: "Utwórz bota",
        path: "/bots/create",
    },
    {
        label: "Oferty do kupienia",
        path: "/action-required",
    },
    {
        label: "Historia",
        path: "/history",
    },
    {
        label: "Cennik modeli",
        path: "/pricing",
    },
    {
        label: "Słowniki",
        path: "/dictionaries",
        end: true,
    },
    {
        label: "Zarządzaj słownikami",
        path: "/dictionaries/manage",
    },
];

function AppLayout() {
    const [isNavigationOpen, setIsNavigationOpen] = useState(false);

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
                            onClick={() => setIsNavigationOpen(false)}
                        >
                            {item.label}
                        </NavLink>
                    ))}
                </nav>

                <div className="sidebar-footer">
                    <div className="sidebar-footer-title">
                        Zasada systemu
                    </div>

                    <div className="sidebar-footer-text">
                        Jedno konto Vinted jest przypisane do jednego bota.
                    </div>
                </div>
            </aside>

            <main className="main-content">
                <Outlet />
            </main>
        </div>
    );
}

export default AppLayout;
