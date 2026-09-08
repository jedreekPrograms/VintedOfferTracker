import { useEffect, useState, type ReactNode } from "react";
import { NavLink, Outlet, useLocation } from "react-router-dom";

type NavigationIconName = "dashboard" | "runtime" | "captcha" | "bots" | "add" | "action" | "history" | "pricing" | "dictionary" | "settings";

interface NavigationItem {
    label: string;
    mobileLabel?: string;
    path: string;
    icon: NavigationIconName;
    end?: boolean;
    mobilePrimary?: boolean;
}

const navigationItems: NavigationItem[] = [
    { label: "Dashboard", path: "/", icon: "dashboard", end: true, mobilePrimary: true },
    { label: "Runtime", path: "/runtime", icon: "runtime", mobilePrimary: true },
    { label: "CAPTCHA", path: "/captcha", icon: "captcha", mobilePrimary: true },
    { label: "Boty", path: "/bots", icon: "bots", mobilePrimary: true },
    { label: "Utwórz bota", path: "/bots/create", icon: "add" },
    { label: "Oferty do kupienia", mobileLabel: "Oferty", path: "/action-required", icon: "action" },
    { label: "Historia", path: "/history", icon: "history" },
    { label: "Cennik modeli", path: "/pricing", icon: "pricing" },
    { label: "Słowniki", path: "/dictionaries", icon: "dictionary", end: true },
    { label: "Zarządzaj słownikami", path: "/dictionaries/manage", icon: "settings" },
];

const primaryMobileItems = navigationItems.filter(item => item.mobilePrimary);

function AppLayout() {
    const [isNavigationOpen, setIsNavigationOpen] = useState(false);
    const location = useLocation();

    useEffect(() => setIsNavigationOpen(false), [location.pathname]);

    useEffect(() => {
        if (!isNavigationOpen) return undefined;
        const previousOverflow = document.body.style.overflow;
        const closeOnEscape = (event: KeyboardEvent) => {
            if (event.key === "Escape") setIsNavigationOpen(false);
        };
        document.body.style.overflow = "hidden";
        window.addEventListener("keydown", closeOnEscape);
        return () => {
            document.body.style.overflow = previousOverflow;
            window.removeEventListener("keydown", closeOnEscape);
        };
    }, [isNavigationOpen]);

    return (
        <div className="app-layout">
            <header className="mobile-app-bar">
                <NavLink className="mobile-app-brand" to="/" aria-label="FlipBot — Dashboard">
                    <span className="mobile-app-logo" aria-hidden="true">F</span>
                    <span><strong>FlipBot</strong><small>Panel zarządzania</small></span>
                </NavLink>
                <button className="mobile-menu-button" type="button" aria-label="Otwórz pełne menu" aria-expanded={isNavigationOpen} onClick={() => setIsNavigationOpen(true)}>
                    <span aria-hidden="true" /><span aria-hidden="true" /><span aria-hidden="true" />
                </button>
            </header>

            <button className={`mobile-navigation-backdrop ${isNavigationOpen ? "mobile-navigation-backdrop-visible" : ""}`} type="button" aria-label="Zamknij nawigację" tabIndex={isNavigationOpen ? 0 : -1} onClick={() => setIsNavigationOpen(false)} />

            <aside className={`sidebar ${isNavigationOpen ? "sidebar-open" : ""}`.trim()} aria-label="Panel nawigacji">
                <div className="sidebar-topbar">
                    <div className="sidebar-header">
                        <div className="sidebar-logo">F</div>
                        <div><div className="sidebar-title">FlipBot</div><div className="sidebar-subtitle">Panel zarządzania</div></div>
                    </div>
                    <button className="mobile-navigation-close" type="button" aria-label="Zamknij nawigację" onClick={() => setIsNavigationOpen(false)}>×</button>
                </div>
                <nav className="sidebar-navigation" aria-label="Główna nawigacja">
                    {navigationItems.map(item => (
                        <NavLink key={item.path} to={item.path} end={item.end} className={({ isActive }) => isActive ? "navigation-link navigation-link-active" : "navigation-link"}>
                            <NavigationIcon name={item.icon} /><span>{item.label}</span>
                        </NavLink>
                    ))}
                </nav>
                <div className="sidebar-footer"><div className="sidebar-footer-title">Zasada systemu</div><div className="sidebar-footer-text">Jedno konto Vinted jest przypisane do jednego bota.</div></div>
            </aside>

            <main className="main-content"><Outlet /></main>

            <nav className="mobile-bottom-navigation" aria-label="Szybka nawigacja">
                {primaryMobileItems.map(item => (
                    <NavLink key={item.path} to={item.path} end={item.end} className={({ isActive }) => isActive ? "mobile-bottom-link mobile-bottom-link-active" : "mobile-bottom-link"}>
                        <NavigationIcon name={item.icon} /><span>{item.mobileLabel ?? item.label}</span>
                    </NavLink>
                ))}
                <button className={`mobile-bottom-link mobile-bottom-more ${isNavigationOpen ? "mobile-bottom-link-active" : ""}`} type="button" aria-label="Więcej ekranów" onClick={() => setIsNavigationOpen(true)}>
                    <NavigationIcon name="settings" /><span>Więcej</span>
                </button>
            </nav>
        </div>
    );
}

function NavigationIcon({ name }: { name: NavigationIconName }) {
    const paths: Record<NavigationIconName, ReactNode> = {
        dashboard: <><path d="M3 11.5 12 4l9 7.5"/><path d="M5.5 10.5V20h13v-9.5"/><path d="M9 20v-6h6v6"/></>,
        runtime: <><path d="M4 12h3l2-5 4 10 2-5h5"/><circle cx="12" cy="12" r="9"/></>,
        captcha: <><path d="M7 8.5V7a5 5 0 0 1 10 0v1.5"/><rect x="4" y="8.5" width="16" height="12" rx="3"/><path d="M9 14h6M12 11v6"/></>,
        bots: <><rect x="4" y="7" width="16" height="12" rx="3"/><path d="M9 3h6M12 3v4M8 12h.01M16 12h.01M9 16h6"/></>,
        add: <><circle cx="12" cy="12" r="9"/><path d="M12 8v8M8 12h8"/></>,
        action: <><path d="M12 3 2.8 20h18.4L12 3Z"/><path d="M12 9v5M12 17h.01"/></>,
        history: <><path d="M4 7v5h5"/><path d="M5.5 17A8 8 0 1 0 4 12"/><path d="M12 8v5l3 2"/></>,
        pricing: <><path d="M4 5h16v14H4z"/><path d="M8 9h8M8 13h3M15.5 16h.01"/></>,
        dictionary: <><path d="M4 5.5A2.5 2.5 0 0 1 6.5 3H11v16H6.5A2.5 2.5 0 0 0 4 21.5z"/><path d="M20 5.5A2.5 2.5 0 0 0 17.5 3H13v16h4.5a2.5 2.5 0 0 1 2.5 2.5z"/></>,
        settings: <><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1-2.8 2.8-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.6v.2h-4V21a1.7 1.7 0 0 0-1-1.6 1.7 1.7 0 0 0-1.9.3l-.1.1L4.2 17l.1-.1a1.7 1.7 0 0 0 .3-1.9A1.7 1.7 0 0 0 3 14H2.8v-4H3a1.7 1.7 0 0 0 1.6-1 1.7 1.7 0 0 0-.3-1.9L4.2 7 7 4.2l.1.1a1.7 1.7 0 0 0 1.9.3A1.7 1.7 0 0 0 10 3V2.8h4V3a1.7 1.7 0 0 0 1 1.6 1.7 1.7 0 0 0 1.9-.3l.1-.1L19.8 7l-.1.1a1.7 1.7 0 0 0-.3 1.9 1.7 1.7 0 0 0 1.6 1h.2v4H21a1.7 1.7 0 0 0-1.6 1Z"/></>,
    };
    return <svg className="navigation-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[name]}</svg>;
}

export default AppLayout;
