package pl.flipbot.command;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;


public interface BotCommandRepository
        extends JpaRepository<BotCommand, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BotCommand>
    findFirstByBotIdAndStatusOrderByIdAsc(
            Long botId,
            BotCommandStatus status
    );

    Optional<BotCommand> findByIdAndBotId(
            Long id,
            Long botId
    );

    boolean existsByBotIdAndListingIdAndTypeAndStatusIn(
            Long botId,
            Long listingId,
            BotCommandType type,
            Collection<BotCommandStatus> statuses
    );

}