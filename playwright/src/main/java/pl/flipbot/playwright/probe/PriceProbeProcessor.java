package pl.flipbot.playwright.probe;

import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.context.BotContext;

import java.util.Optional;

@Slf4j
public class PriceProbeProcessor {

    private final BotContext context;
    private final PriceProbeRuntimeConfig config;
    private final PriceProbeApiClient apiClient;
    private final PriceProbeExecutor executor;

    public PriceProbeProcessor(
            BotContext context,
            PriceProbeRuntimeConfig config
    ) {
        this.context = context;
        this.config = config;
        this.apiClient = new PriceProbeApiClient();
        this.executor = new PriceProbeExecutor(context, config);
    }

    public int processOne() {
        if (!config.enabled()) {
            return 0;
        }

        Long botId = context.getBot().getId();
        Optional<PriceProbeAssignmentDto> assignment = apiClient.claimNext(botId);

        if (assignment.isEmpty()) {
            log.info(
                    "[PRICE PROBE] Bot {} has no eligible probe assignment.",
                    botId
            );
            return 0;
        }

        PriceProbeAssignmentDto probe = assignment.get();

        if (!config.enabled()) {
            apiClient.complete(
                    botId,
                    probe.probeId(),
                    PriceProbeOutcomeDto.failed("PRICE_PROBE disabled before execution.")
            );
            return 0;
        }

        PriceProbeExecutionResult result = executor.execute(probe);
        PriceProbeOutcomeDto outcome = switch (result.state()) {
            case SENT -> PriceProbeOutcomeDto.sent();
            case FAILED -> PriceProbeOutcomeDto.failed(result.details());
            case UNKNOWN -> PriceProbeOutcomeDto.unknown(result.details());
        };

        try {
            apiClient.complete(botId, probe.probeId(), outcome);
        } catch (RuntimeException exception) {
            log.error(
                    "[PRICE PROBE] Could not report outcome {} for probe {} / bot {}. The browser action will NOT be retried automatically.",
                    result.state(),
                    probe.probeId(),
                    botId,
                    exception
            );
        }

        return 1;
    }
}
