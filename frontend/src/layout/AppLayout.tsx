import { NavLink, Outlet } from "react-router-dom";

interface NavigationItem {
    label: string;
    path: string;
    end?: boolean
}

const navigationItems: NavigationItem[] = [
    {
        label: "Dashboard",
        path: "/",
        end: true,
    },
    {
        label: "Boty",
        path:"/bots",
    },
    {   label: "Utwórz bota",
        path: "/bots/create",
    },
    {
        label: "Oferty do kupienia",
        path: "/action-required",
    },
    {
        label: "Słowniki",
        path: "/dictionaries"
    },
];

function AppLayout() {
    return (
        <div className="app-layout">
            <aside className="sidebar">
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

                <nav className="sidebar-navigation">
                    {navigationItems.map((item) => (
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
                            {item.label}
                        </NavLink>        
                    ))}
                </nav>

                <div className="sidebar-footer">
                    <div className="sidebar-footer-title">
                        Zasada systemu
                    </div>

                    <div className="sidebar-footer-text">
                        Jedno konto Vinted jest przypisane do jednego  bota.
                    </div>
                </div>
            </aside>

            <main className="main-content">
                <Outlet />
            </main>
        </div>
    )
}

export default AppLayout