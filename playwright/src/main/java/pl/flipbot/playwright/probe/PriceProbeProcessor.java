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

    public int process() {
        if (!config.enabled()) {
            return 0;
        }

        Long botId = context.getBot().getId();
        int processed = 0;

        for (int index = 0; index < config.maxPerJob(); index++) {
            Optional<PriceProbeAssignmentDto> assignment =
                    apiClient.claimNext(botId);

            if (assignment.isEmpty()) {
                if (processed == 0) {
                    log.info(
                            "[PRICE PROBE] Bot {} has no eligible sandbox probe assignment.",
                            botId
                    );
                }
                break;
            }

            PriceProbeAssignmentDto probe = assignment.get();
            PriceProbeExecutionResult result = executor.execute(probe);
            processed++;

            PriceProbeOutcomeDto outcome = switch (result.state()) {
                case SENT -> PriceProbeOutcomeDto.sent();
                case FAILED -> PriceProbeOutcomeDto.failed(result.details());
                case UNKNOWN -> PriceProbeOutcomeDto.unknown(result.details());
            };

            try {
                apiClient.complete(
                        botId,
                        probe.probeId(),
                        outcome
                );
            } catch (RuntimeException exception) {
                /*
                 * Do not retry the browser action. The backend claim was
                 * persisted before execution and the unique bot/listing key
                 * prevents a later duplicate even if completion reporting is
                 * temporarily unavailable.
                 */
                log.error(
                        "[PRICE PROBE] Could not report outcome {} for probe {} / bot {}. The browser action will NOT be retried automatically.",
                        result.state(),
                        probe.probeId(),
                        botId,
                        exception
                );
            }
        }

        log.info(
                "[PRICE PROBE] Bot {} sandbox probe job finished. processed={}.",
                botId,
                processed
        );

        return processed;
    }
}
