package pl.flipbot.playwright.login;

import com.microsoft.playwright.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.model.BotDetailsDto;

@Slf4j
@RequiredArgsConstructor
public class LoginService {

    private final Page page;
    private final BotDetailsDto bot;

    public void login() {

        log.info(
                "Logging in bot {}",
                bot.getId()
        );
    }
}
