import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import App from "./App";
import "./index.css";
import "./styles/commercial-ui.css";
import "./styles/commercial-ui-details.css";

const rootElement = document.getElementById("root");

if (rootElement === null) {
    throw new Error("Root element was not found");
}

createRoot(rootElement).render(
    <StrictMode>
        <BrowserRouter>
            <App></App>
        </BrowserRouter>
    </StrictMode>,
);
