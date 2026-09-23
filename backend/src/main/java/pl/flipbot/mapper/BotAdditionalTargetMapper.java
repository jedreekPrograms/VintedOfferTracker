package pl.flipbot.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.flipbot.bot.configuration.BotAdditionalTarget;
import pl.flipbot.bot.configuration.BotConfiguration;
import pl.flipbot.bot.dto.BotAdditionalTargetResponse;

@Component
@RequiredArgsConstructor
public class BotAdditionalTargetMapper {

    private final NegotiationStepMapper negotiationStepMapper;

    public BotAdditionalTargetResponse map(BotAdditionalTarget target) {
        BotConfiguration main = target.getConfiguration();

        return BotAdditionalTargetResponse.builder()
                .additionalTargetId(target.getId())
                .active(target.getActive())
                .marketplace(main.getMarketplace())
                .categoryPath(target.getCategoryPath())
                .brand(target.getBrand())
                .targetMode(target.getTargetMode())
                .model(target.getModel())
                .searchQuery(target.getSearchQuery())
                .minPrice(target.getMinPrice())
                .maxPrice(target.getMaxPrice())
                .autoRaiseOfferToVintedMinimum(
                        target.getAutoRaiseOfferToVintedMinimum()
                )
                .maxAutomaticOffer(target.getMaxAutomaticOffer())
                .dailyNegotiationBudget(main.getDailyNegotiationBudget())
                .negotiationSteps(
                        target.getNegotiationSteps()
                                .stream()
                                .sorted((left, right) -> Integer.compare(
                                        left.getStepNumber(),
                                        right.getStepNumber()
                                ))
                                .map(negotiationStepMapper::map)
                                .toList()
                )
                .build();
    }
}
