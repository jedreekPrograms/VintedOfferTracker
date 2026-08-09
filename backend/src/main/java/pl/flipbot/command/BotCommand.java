package pl.flipbot.command;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
        name = "bot_command",
        indexes = {
                @Index(
                        name = "idx_bot_command_bot_status",
                        columnList = "bot_id,status,id"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
public class BotCommand {

    @Id
    @GeneratedValue(
            strategy = GenerationType.IDENTITY
    )
    private Long id;

    @Column(
            name = "bot_id",
            nullable = false
    )
    private Long botId;

    @Column(
            name = "listing_id",
            nullable = false
    )
    private Long listingId;

    @Enumerated(
            EnumType.STRING
    )
    @Column(
            nullable = false,
            length = 50
    )
    private BotCommandType type;

    @Enumerated(
            EnumType.STRING
    )
    @Column(
            nullable = false,
            length = 50
    )
    private BotCommandStatus status;

    @Column(
            name = "error_message",
            length = 1000
    )
    private String errorMessage;

    @Column(
            name = "created_at",
            nullable = false
    )
    private Instant createdAt;

    @Column(
            name = "processed_at"
    )
    private Instant processedAt;

    @PrePersist
    private void prePersist() {

        if (status == null) {
            status =
                    BotCommandStatus.PENDING;
        }

        if (createdAt == null) {
            createdAt =
                    Instant.now();
        }
    }
}