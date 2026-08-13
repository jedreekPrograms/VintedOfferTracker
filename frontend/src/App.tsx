import {
    Navigate,
    Route,
    Routes,
} from "react-router-dom";

import AppLayout from "./layout/AppLayout";

import DashboardPage from "./pages/DashboardPage";
import RuntimeDashboardPage from "./pages/RuntimeDashboardPage";
import BotsPage from "./pages/BotsPage";
import CreateBotPage from "./pages/CreateBotPage";
import EditBotWithDeletePage from "./pages/EditBotWithDeletePage";
import ActionRequiredPage from "./pages/ActionRequiredPage";
import HistoryPage from "./pages/HistoryPage";
import DictionariesPage from "./pages/DictionariesPage";
import ManageDictionariesPage from "./pages/ManageDictionariesPage";


function App() {

    return (

        <Routes>

            <Route element={<AppLayout />}>

                <Route
                    index
                    element={<DashboardPage />}
                />

                <Route
                    path="/runtime"
                    element={<RuntimeDashboardPage />}
                />

                <Route
                    path="/bots"
                    element={<BotsPage />}
                />

                <Route
                    path="/bots/create"
                    element={<CreateBotPage />}
                />

                <Route
                    path="/bots/:botId/edit"
                    element={<EditBotWithDeletePage />}
                />

                <Route
                    path="/action-required"
                    element={<ActionRequiredPage />}
                />

                <Route
                    path="/history"
                    element={<HistoryPage />}
                />

                <Route
                    path="/dictionaries"
                    element={<DictionariesPage />}
                />

                <Route
                    path="/dictionaries/manage"
                    element={<ManageDictionariesPage />}
                />

            </Route>


            <Route
                path="*"
                element={
                    <Navigate
                        to="/"
                        replace
                    />
                }
            />

        </Routes>
    );
}


export default App;
