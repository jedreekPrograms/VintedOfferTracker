import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import App from "./App";
import { registerFlipBotServiceWorker } from "./pwa";
import "./index.css";
import "./styles/commercial-ui.css";
import "./styles/commercial-ui-details.css";
import "./styles/negotiation-response-policies.css";
import "./styles/price-matrix-edge-fix.css";
import "./styles/mobile-app.css";

const rootElement = document.getElementById("root");

if (rootElement === null) {
    throw new Error("Root element was not found");
}

registerFlipBotServiceWorker();

createRoot(rootElement).render(
    <StrictMode>
        <BrowserRouter>
            <App></App>
        </BrowserRouter>
    </StrictMode>,
);
