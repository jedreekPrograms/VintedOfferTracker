package pl.flipbot.bot.runtime;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BotRuntimeStateRepository
        extends JpaRepository<BotRuntimeState, Long> {
}
