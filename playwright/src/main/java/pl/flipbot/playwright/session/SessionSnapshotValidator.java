package pl.flipbot.playwright.session;

import com.fasterxml.jackson.databind.JsonNode;

final class SessionSnapshotValidator {

    private SessionSnapshotValidator() {
    }

    static void validateShape(Long botId, JsonNode root) {
        if (root == null
                || !root.isObject()
                || !root.path("cookies").isArray()
                || !root.path("origins").isArray()) {
            throw new IllegalStateException(
                    "Playwright produced an invalid storageState JSON for bot "
                            + botId
                            + "; refusing to replace the active session."
            );
        }
    }

    static void validateDoesNotLoseEstablishedCookies(
            Long botId,
            JsonNode active,
            JsonNode staged
    ) {
        if (active == null || staged == null) {
            return;
        }

        JsonNode activeCookies = active.path("cookies");
        JsonNode stagedCookies = staged.path("cookies");

        if (!activeCookies.isArray() || !stagedCookies.isArray()) {
            return;
        }

        if (!activeCookies.isEmpty() && stagedCookies.isEmpty()) {
            throw new IllegalStateException(
                    "Playwright produced a cookie-empty session state for bot "
                            + botId
                            + " while the active session still contains cookies; refusing to replace the last-known-good session."
            );
        }
    }
}
