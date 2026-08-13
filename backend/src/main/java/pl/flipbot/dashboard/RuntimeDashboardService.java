package pl.flipbot.dashboard;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.flipbot.bot.Bot;
import pl.flipbot.bot.BotRepository;
import pl.flipbot.bot.BotStatus;
import pl.flipbot.bot.runtime.BotRuntimeState;
import pl.flipbot.bot.runtime.BotRuntimeStateRepository;
import pl.flipbot.bot.runtime.BotRuntimeStatus;
import pl.flipbot.dashboard.dto.RuntimeDashboardBotResponse;
import pl.flipbot.dashboard.dto.RuntimeDashboardResponse;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RuntimeDashboardService {

    private final BotRepository botRepository;
    private final BotRuntimeStateRepository runtimeStateRepository;

    @Transactional(readOnly = true)
    public RuntimeDashboardResponse getRuntimeDashboard() {
        List<Bot> bots = botRepository.findAll();

        Map<Long, BotRuntimeState> runtimeByBotId =
                runtimeStateRepository.findAll()
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        BotRuntimeState::getBotId,
                                        Function.identity()
                                )
                        );

        List<RuntimeDashboardBotResponse> rows =
                bots.stream()
                        .map(bot -> toRow(bot, runtimeByBotId.get(bot.getId())))
                        .sorted(runtimeOrdering())
                        .toList();

        long runningBots = bots.stream()
                .filter(bot -> bot.getStatus() == BotStatus.RUNNING)
                .count();

        long idleCount = countStatus(rows, BotRuntimeStatus.IDLE);
        long queuedCount = countStatus(rows, BotRuntimeStatus.QUEUED);
        long workingCount = countStatus(rows, BotRuntimeStatus.WORKING);
        long cooldownCount = countStatus(rows, BotRuntimeStatus.COOLDOWN);
        long errorCount = countStatus(rows, BotRuntimeStatus.ERROR);

        double averageLastRunDurationMs =
                rows.stream()
                        .map(RuntimeDashboardBotResponse::lastRunDurationMs)
                        .filter(duration -> duration != null && duration >= 0)
                        .mapToLong(Long::longValue)
                        .average()
                        .orElse(0.0);

        return new RuntimeDashboardResponse(
                bots.size(),
                runningBots,
                idleCount,
                queuedCount,
                workingCount,
                cooldownCount,
                errorCount,
                averageLastRunDurationMs,
                rows
        );
    }

    private RuntimeDashboardBotResponse toRow(
            Bot bot,
            BotRuntimeState runtime
    ) {
        BotRuntimeStatus runtimeStatus =
                runtime == null
                        ? BotRuntimeStatus.IDLE
                        : runtime.getRuntimeStatus();

        return new RuntimeDashboardBotResponse(
                bot.getId(),
                bot.getName(),
                bot.getStatus().name(),
                runtimeStatus.name(),
                runtime == null ? null : runtime.getLastRunStartedAt(),
                runtime == null ? null : runtime.getLastRunFinishedAt(),
                runtime == null ? null : runtime.getNextRunAt(),
                runtime == null ? null : runtime.getLastRunDurationMs(),
                runtime == null ? 0 : runtime.getConsecutiveFailures(),
                runtime == null ? null : runtime.getLastError(),
                runtime == null ? null : runtime.getWorkerSlot(),
                runtime == null ? null : runtime.getUpdatedAt()
        );
    }

    private long countStatus(
            List<RuntimeDashboardBotResponse> rows,
            BotRuntimeStatus status
    ) {
        String expected = status.name();

        return rows.stream()
                .filter(row -> expected.equals(row.runtimeStatus()))
                .count();
    }

    private Comparator<RuntimeDashboardBotResponse> runtimeOrdering() {
        return Comparator
                .comparingInt(
                        (RuntimeDashboardBotResponse row) ->
                                statusPriority(row.runtimeStatus())
                )
                .thenComparing(
                        RuntimeDashboardBotResponse::botId,
                        Comparator.nullsLast(Long::compareTo)
                );
    }

    private int statusPriority(String status) {
        return switch (status) {
            case "ERROR" -> 0;
            case "WORKING" -> 1;
            case "COOLDOWN" -> 2;
            case "QUEUED" -> 3;
            default -> 4;
        };
    }
}
