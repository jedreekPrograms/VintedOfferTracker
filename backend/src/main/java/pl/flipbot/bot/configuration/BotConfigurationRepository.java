package pl.flipbot.bot.configuration;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BotConfigurationRepository extends JpaRepository<BotConfiguration, Long> {
}
