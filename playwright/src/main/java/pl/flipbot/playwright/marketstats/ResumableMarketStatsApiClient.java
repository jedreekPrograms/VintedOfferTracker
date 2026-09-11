package pl.flipbot.playwright.marketstats;

import pl.flipbot.playwright.marketstats.dto.KnownMarketListingIdsDto;
import pl.flipbot.playwright.marketstats.dto.MarketStatsTargetDto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ResumableMarketStatsApiClient extends MarketStatsApiClient {

    private final int requestedStartIndex;
    private final Map<Long, Integer> originalIndexByModelId =
            new HashMap<>();

    private int targetCount;
    private int normalizedStartIndex;
    private int currentOriginalIndex = -1;

    ResumableMarketStatsApiClient(int requestedStartIndex) {
        this.requestedStartIndex = requestedStartIndex;
    }

    @Override
    public List<MarketStatsTargetDto> getTargets() {
        List<MarketStatsTargetDto> targets = super.getTargets();

        targetCount = targets == null ? 0 : targets.size();
        normalizedStartIndex = MarketStatsTargetRotation.normalizeStartIndex(
                requestedStartIndex,
                targetCount
        );
        currentOriginalIndex = -1;
        originalIndexByModelId.clear();

        if (targets == null || targets.isEmpty()) {
            return List.of();
        }

        for (int index = 0; index < targets.size(); index++) {
            MarketStatsTargetDto target = targets.get(index);
            if (target != null && target.modelId() != null) {
                originalIndexByModelId.put(target.modelId(), index);
            }
        }

        return MarketStatsTargetRotation.rotate(
                targets,
                normalizedStartIndex
        );
    }

    @Override
    public KnownMarketListingIdsDto getKnownListingIds(Long modelId) {
        Integer originalIndex = originalIndexByModelId.get(modelId);
        if (originalIndex != null) {
            currentOriginalIndex = originalIndex;
        }

        return super.getKnownListingIds(modelId);
    }

    int resumeIndexAfterCurrentTarget() {
        if (targetCount <= 0) {
            return 0;
        }

        if (currentOriginalIndex < 0) {
            return normalizedStartIndex;
        }

        return MarketStatsTargetRotation.nextIndex(
                currentOriginalIndex,
                targetCount
        );
    }
}
