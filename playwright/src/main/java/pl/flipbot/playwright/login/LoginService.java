package pl.flipbot.playwright.login;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.model.BotDetailsDto;

@Slf4j
@RequiredArgsConstructor
public class LoginService {

    private static final String HOME_URL =
            "https://www.vinted.pl/";

    private final BotContext context;

    public void login() {

        Page page = context.getPage();

        page.navigate(HOME_URL);

        if(isLoggedIn()) {

            log.info(
                    "Bot {} is already logged in.",
                    context.getBot().getId()
            );

            return;

        }

        performLogin();

    }

    private boolean isLoggedIn() {

        try {

            return context.getPage()
                    .getByTestId(LoginSelectors.CONVERSATIONS_BUTTON)
                    .isVisible();

        } catch (Exception e) {

            return false;

        }

    }

    private void performLogin() {

        Page page = context.getPage();

        log.info(
                "Logging in {}",
                context.getBot().getEmail()
        );

        page.getByTestId(LoginSelectors.LOGIN_BUTTON)
                .click();

        page.getByTestId(LoginSelectors.LOGIN_BY_EMAIL)
                .click();

        page.locator("#" + LoginSelectors.EMAIL_INPUT)
                .fill(context.getBot().getEmail());

        page.locator("#" + LoginSelectors.PASSWORD_INPUT)
                .fill(context.getBot().getPassword());

        page.getByRole(
                AriaRole.BUTTON,
                new Page.GetByRoleOptions()
                        .setName(LoginSelectors.SUBMIT_BUTTON)
        ).click();

        page.getByTestId(
                LoginSelectors.CONVERSATIONS_BUTTON
        ).waitFor();

        log.info(
                "Bot {} logged in successfully.",
                context.getBot().getId()
        );

    }

}
