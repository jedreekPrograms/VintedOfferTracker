package pl.flipbot.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.flipbot.bot.configuration.BotConfiguration;
import pl.flipbot.bot.dto.BotConfigurationResponse;

@Component
@RequiredArgsConstructor
public class BotConfigurationMapper {

    private final NegotiationStepMapper negotiationStepMapper;

    public BotConfigurationResponse map(BotConfiguration configuration) {

        return BotConfigurationResponse.builder()
                .marketplace(configuration.getMarketplace())
                .categoryPath(configuration.getCategoryPath())
                .brand(configuration.getBrand())
                .model(configuration.getModel())
                .minPrice(configuration.getMinPrice())
                .maxPrice(configuration.getMaxPrice())
                .negotiationSteps(
                        configuration.getNegotiationSteps()
                                .stream()
                                .map(negotiationStepMapper::map)
                                .toList()
                )
                .build();

    }

}