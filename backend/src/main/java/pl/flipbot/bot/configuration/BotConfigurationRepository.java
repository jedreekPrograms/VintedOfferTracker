package pl.flipbot.bot.configuration;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BotConfigurationRepository
        extends JpaRepository<BotConfiguration, Long> {

    List<BotConfiguration> findAllByBrandIgnoreCase(
            String brand
    );

    List<BotConfiguration> findAllByBrandIgnoreCaseAndModelIgnoreCase(
            String brand,
            String model
    );
}
