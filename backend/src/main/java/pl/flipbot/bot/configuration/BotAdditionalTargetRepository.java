package pl.flipbot.bot.configuration;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BotAdditionalTargetRepository
        extends JpaRepository<BotAdditionalTarget, Long> {

    List<BotAdditionalTarget> findAllByConfigurationBotIdOrderByIdAsc(
            Long botId
    );

    List<BotAdditionalTarget> findAllByConfigurationBotIdAndActiveTrueOrderByIdAsc(
            Long botId
    );

    Optional<BotAdditionalTarget> findByIdAndConfigurationBotId(
            Long targetId,
            Long botId
    );

    long countByConfigurationBotIdAndActiveTrue(
            Long botId
    );
}
