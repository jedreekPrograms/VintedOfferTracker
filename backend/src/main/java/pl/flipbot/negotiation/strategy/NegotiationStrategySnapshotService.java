package pl.flipbot.negotiation.strategy;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.flipbot.bot.configuration.BotAdditionalTarget;
import pl.flipbot.bot.configuration.BotConfiguration;
import pl.flipbot.listing.Listing;
import pl.flipbot.negotiation.NegotiationStep;

import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class NegotiationStrategySnapshotService {

    private final JsonMapper jsonMapper;

    public boolean pinIfMissing(Listing listing) {
        if (listing == null) {
            throw new IllegalArgumentException("Listing is required.");
        }
        if (hasText(listing.getNegotiationStrategySnapshot())) {
            return false;
        }

        NegotiationStrategySnapshot snapshot = listing.getAdditionalTarget() == null
                ? captureMain(listing)
                : captureAdditional(listing.getAdditionalTarget());

        listing.setNegotiationStrategyVersion(snapshot.version());
        listing.setNegotiationStrategySnapshot(serialize(snapshot));
        return true;
    }

    public NegotiationStrategySnapshot read(Listing listing) {
        if (listing == null || !hasText(listing.getNegotiationStrategySnapshot())) {
            return null;
        }
        try {
            return jsonMapper.readValue(
                    listing.getNegotiationStrategySnapshot(),
                    NegotiationStrategySnapshot.class
            );
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "Cannot read negotiation strategy snapshot for listing "
                            + listing.getId(),
                    exception
            );
        }
    }

    public NegotiationStrategySnapshot capture(BotConfiguration configuration) {
        if (configuration == null) {
            throw new IllegalStateException("Main bot configuration is missing.");
        }
        return new NegotiationStrategySnapshot(
                normalizedVersion(configuration.getNegotiationStrategyVersion()),
                Boolean.TRUE.equals(configuration.getAutoRaiseOfferToVintedMinimum()),
                configuration.getMaxAutomaticOffer(),
                orderedSteps(configuration.getNegotiationSteps())
                        .stream()
                        .map(this::snapshotStep)
                        .toList()
        );
    }

    public NegotiationStrategySnapshot capture(BotAdditionalTarget target) {
        if (target == null) {
            throw new IllegalStateException("Additional target is missing.");
        }
        return new NegotiationStrategySnapshot(
                normalizedVersion(target.getNegotiationStrategyVersion()),
                Boolean.TRUE.equals(target.getAutoRaiseOfferToVintedMinimum()),
                target.getMaxAutomaticOffer(),
                orderedSteps(target.getNegotiationSteps())
                        .stream()
                        .map(this::snapshotStep)
                        .toList()
        );
    }

    private NegotiationStrategySnapshot captureMain(Listing listing) {
        if (listing.getBot() == null || listing.getBot().getConfiguration() == null) {
            throw new IllegalStateException(
                    "Cannot snapshot main negotiation strategy for listing "
                            + listing.getId()
                            + " because bot configuration is missing."
            );
        }
        return capture(listing.getBot().getConfiguration());
    }

    private NegotiationStrategySnapshot captureAdditional(
            BotAdditionalTarget target
    ) {
        return capture(target);
    }

    private NegotiationStrategySnapshot.NegotiationStepSnapshot snapshotStep(
            NegotiationStep step
    ) {
        return new NegotiationStrategySnapshot.NegotiationStepSnapshot(
                step.getStepNumber(),
                step.getOfferPrice(),
                step.getMaxAcceptedCounterOffer(),
                step.getMessage(),
                step.getRejectionAction(),
                step.getRejectionWaitHours(),
                step.getReadWaitHours(),
                step.getUnreadWaitHours(),
                step.getCounterOfferDefaultAction(),
                step.getCounterOfferDefaultWaitHours(),
                step.getCounterOfferRules() == null
                        ? List.of()
                        : step.getCounterOfferRules()
                        .stream()
                        .map(rule -> new NegotiationStrategySnapshot.SellerCounterOfferRuleSnapshot(
                                rule.getMinimumDiscountPercent(),
                                rule.getAction(),
                                rule.getWaitHours()
                        ))
                        .toList()
        );
    }

    private List<NegotiationStep> orderedSteps(List<NegotiationStep> steps) {
        if (steps == null) {
            return List.of();
        }
        return steps.stream()
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(
                        step -> step.getStepNumber() == null
                                ? Integer.MAX_VALUE
                                : step.getStepNumber()
                ))
                .toList();
    }

    private String serialize(NegotiationStrategySnapshot snapshot) {
        try {
            return jsonMapper.writeValueAsString(snapshot);
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "Cannot serialize negotiation strategy snapshot.",
                    exception
            );
        }
    }

    private int normalizedVersion(Integer version) {
        return version == null || version < 1 ? 1 : version;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
