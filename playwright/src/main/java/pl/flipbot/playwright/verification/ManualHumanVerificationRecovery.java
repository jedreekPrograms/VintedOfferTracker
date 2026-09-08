package pl.flipbot.playwright.verification;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.browser.BrowserManager;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.login.LoginService;
import pl.flipbot.playwright.marketplace.MarketplaceUrls;
import pl.flipbot.playwright.model.BotDetailsDto;

import java.net.URI;
import java.util.Objects;
import java.util.concurrent.Semaphore;

/**
 * Opens a temporary headed browser so a human can complete a challenge.
 *
 * <p>Only one recovery window is exposed at a time. The caller must first
 * close the failed headless context, which guarantees that bot-X.json is not
 * used concurrently by the invisible and visible browsers.</p>
 */
@Slf4j
public class ManualHumanVerificationRecovery {

    private static final double NAVIGATION_TIMEOUT_MS = 30_000;
    private static final double CHALLENGE_RENDER_GRACE_MS = 1_000;

    private static final Semaphore VISIBLE_RECOVERY_WINDOW =
            new Semaphore(1, true);

    private final ManualVerificationRuntimeConfig config;
    private final HumanVerificationHandler verificationHandler;

    public ManualHumanVerificationRecovery() {
        this(
                ManualVerificationRuntimeConfig.fromEnvironment(),
                new HumanVerificationHandler()
        );
    }

    ManualHumanVerificationRecovery(
            ManualVerificationRuntimeConfig config,
            HumanVerificationHandler verificationHandler
    ) {
        this.config = Objects.requireNonNull(config, "Config cannot be null");
        this.verificationHandler = Objects.requireNonNull(
                verificationHandler,
                "Verification handler cannot be null"
        );
    }

    public void recover(
            BotDetailsDto bot,
            HumanVerificationRequiredException challenge
    ) throws InterruptedException {
        Objects.requireNonNull(bot, "Bot cannot be null");
        Objects.requireNonNull(challenge, "Challenge cannot be null");

        Long botId = bot.getId();
        if (botId == null || botId <= 0L) {
            throw new IllegalArgumentException(
                    "Manual verification requires a bot with a positive ID"
            );
        }

        if (VISIBLE_RECOVERY_WINDOW.availablePermits() == 0) {
            log.warn(
                    "[CAPTCHA] Bot {} is waiting because another bot already has the single manual verification window.",
                    botId
            );
        }

        VISIBLE_RECOVERY_WINDOW.acquire();

        try {
            String recoveryUrl = recoveryUrl(challenge.challengeUrl());

            log.warn(
                    "[CAPTCHA] Opening a visible browser for bot {} with the same bot-{}.json session. "
                            + "Complete the challenge manually within {} seconds. url={}, evidence={}",
                    botId,
                    botId,
                    config.timeoutSeconds(),
                    recoveryUrl,
                    challenge.evidence()
            );

            try (BrowserManager browserManager = new BrowserManager(false);
                 BotContext context = new BotContext(bot, browserManager)) {
                Page page = context.getPage();

                if (requiresInteractiveLoginReplay(recoveryUrl)) {
                    replayAuthenticationFlow(context, botId, recoveryUrl);
                } else {
                    navigateToChallenge(page, recoveryUrl);
                    page.waitForTimeout(CHALLENGE_RENDER_GRACE_MS);

                    verificationHandler.waitUntilManuallyVerified(
                            page,
                            config.timeoutMillis()
                    );
                }

                context.saveSession();

                log.info(
                        "[CAPTCHA] Manual verification finished for bot {}. The refreshed state was saved to the same bot-{}.json session.",
                        botId,
                        botId
                );
            }
        } finally {
            VISIBLE_RECOVERY_WINDOW.release();
        }
    }

    static String recoveryUrl(String challengeUrl) {
        if (MarketplaceUrls.isVintedUrl(challengeUrl)) {
            return challengeUrl.trim();
        }

        return MarketplaceUrls.HOME;
    }

    static boolean requiresInteractiveLoginReplay(String recoveryUrl) {
        if (!MarketplaceUrls.isVintedUrl(recoveryUrl)) {
            return false;
        }

        try {
            String path = URI.create(recoveryUrl.trim()).getPath();
            if (path == null) {
                return false;
            }

            return path.equals("/member/login")
                    || path.startsWith("/member/login/")
                    || path.equals("/member/register")
                    || path.startsWith("/member/register/");
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private void replayAuthenticationFlow(
            BotContext context,
            Long botId,
            String recoveryUrl
    ) {
        log.warn(
                "[CAPTCHA] Bot {} challenge originated from Vinted authentication ({}). "
                        + "Replaying the normal login flow in this visible browser so credential submission can render the CAPTCHA here instead of falsely accepting an empty pre-submit page.",
                botId,
                recoveryUrl
        );

        new LoginService(context).login();
    }

    private void navigateToChallenge(
            Page page,
            String recoveryUrl
    ) {
        try {
            page.navigate(
                    recoveryUrl,
                    new Page.NavigateOptions()
                            .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                            .setTimeout(NAVIGATION_TIMEOUT_MS)
            );
        } catch (PlaywrightException exception) {
            if (page.isClosed()
                    || !verificationHandler.isHumanVerificationVisible(page)) {
                throw exception;
            }

            log.warn(
                    "[CAPTCHA] Navigation did not reach DOMContentLoaded, but the visible challenge is rendered. Keeping the recovery window open for manual completion. url={}",
                    safePageUrl(page)
            );
        }
    }

    private String safePageUrl(Page page) {
        try {
            String url = page.url();
            return url == null ? "" : url;
        } catch (PlaywrightException exception) {
            return "";
        }
    }
}
