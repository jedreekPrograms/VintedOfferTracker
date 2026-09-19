package pl.flipbot.mobile;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the bundled React panel for browser/mobile navigation while leaving
 * every /api endpoint on the existing backend unchanged.
 */
@Controller
public class MobilePanelController {

    @GetMapping({
            "/runtime",
            "/bots",
            "/bots/create",
            "/bots/{botId}/edit",
            "/action-required",
            "/history",
            "/pricing",
            "/dictionaries",
            "/dictionaries/manage"
    })
    public String forwardPanelRoute() {
        return "forward:/index.html";
    }
}
