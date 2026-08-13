package pl.flipbot.bot.runtime;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.flipbot.bot.Bot;

import java.time.Instant;

@Entity
@Table(name = "bot_runtime_state")
@Getter
@Setter
@NoArgsConstructor
public class BotRuntimeState {

    @Id
    @Column(name = "bot_id")
    private Long botId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "bot_id")
    private Bot bot;

    @Enumerated(EnumType.STRING)
    @Column(name = "runtime_status", nullable = false)
    private BotRuntimeStatus runtimeStatus;

    @Column(name = "last_run_started_at")
    private Instant lastRunStartedAt;

    @Column(name = "last_run_finished_at")
    private Instant lastRunFinishedAt;

    @Column(name = "next_run_at")
    private Instant nextRunAt;

    @Column(name = "last_run_duration_ms")
    private Long lastRunDurationMs;

    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "worker_slot")
    private Integer workerSlot;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
