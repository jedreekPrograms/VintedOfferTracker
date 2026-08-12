package pl.flipbot.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.flipbot.bot.configuration.BotConfiguration;
import pl.flipbot.bot.dto.BotConfigurationResponse;

@Component
@RequiredArgsConstructor
public class BotConfigurationMapper {

    private final NegotiationStepMapper negotiationStepMapper;

    public BotConfigurationResponse map(
            BotConfiguration configuration
    ) {

        return BotConfigurationResponse.builder()
                .marketplace(
                        configuration.getMarketplace()
                )
                .categoryPath(
                        configuration.getCategoryPath()
                )
                .brand(
                        configuration.getBrand()
                )
                .targetMode(
                        configuration.getTargetMode()
                )
                .model(
                        configuration.getModel()
                )
                .searchQuery(
                        configuration.getSearchQuery()
                )
                .minPrice(
                        configuration.getMinPrice()
                )
                .maxPrice(
                        configuration.getMaxPrice()
                )
                .autoRaiseOfferToVintedMinimum(
                        configuration.getAutoRaiseOfferToVintedMinimum()
                )
                .maxAutomaticOffer(
                        configuration.getMaxAutomaticOffer()
                )
                .dailyNegotiationBudget(
                        configuration.getDailyNegotiationBudget()
                )
                .negotiationSteps(
                        configuration.getNegotiationSteps()
                                .stream()
                                .map(
                                        negotiationStepMapper::map
                                )
                                .toList()
                )
                .build();
    }
}
