package pl.flipbot.playwright.marketstats;

import pl.flipbot.playwright.marketstats.dto.KnownMarketListingIdsDto;
import pl.flipbot.playwright.marketstats.dto.MarketStatsTargetDto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ResumableMarketStatsApiClient extends MarketStatsApiClient {

    private final int requestedStartIndex;
    private final int maxTargetsPerBatch;
    private final List<Long> preferredTargetOrder;
    private final Map<Long, Integer> originalIndexByModelId =
            new HashMap<>();

    private int targetCount;
    private int returnedTargetCount;
    private int normalizedStartIndex;
    private int currentOriginalIndex = -1;
    private List<Long> targetOrderModelIds = List.of();

    ResumableMarketStatsApiClient(int requestedStartIndex) {
        this(
                requestedStartIndex,
                Integer.MAX_VALUE,
                List.of()
        );
    }

    ResumableMarketStatsApiClient(
            int requestedStartIndex,
            int maxTargetsPerBatch
    ) {
        this(
                requestedStartIndex,
                maxTargetsPerBatch,
                List.of()
        );
    }

    ResumableMarketStatsApiClient(
            int requestedStartIndex,
            int maxTargetsPerBatch,
            List<Long> preferredTargetOrder
    ) {
        this.requestedStartIndex = requestedStartIndex;
        this.maxTargetsPerBatch = Math.max(1, maxTargetsPerBatch);
        this.preferredTargetOrder = preferredTargetOrder == null
                ? List.of()
                : List.copyOf(preferredTargetOrder);
    }

    @Override
    public List<MarketStatsTargetDto> getTargets() {
        List<MarketStatsTargetDto> targets = applyPreferredTargetOrder(
                super.getTargets(),
                preferredTargetOrder
        );

        targetCount = targets.size();
        returnedTargetCount = 0;
        normalizedStartIndex = MarketStatsTargetRotation.normalizeStartIndex(
                requestedStartIndex,
                targetCount
        );
        currentOriginalIndex = -1;
        originalIndexByModelId.clear();

        if (targets.isEmpty()) {
            targetOrderModelIds = List.of();
            return List.of();
        }

        targetOrderModelIds = targets.stream()
                .filter(target -> target != null && target.modelId() != null)
                .map(MarketStatsTargetDto::modelId)
                .toList();

        for (int index = 0; index < targets.size(); index++) {
            MarketStatsTargetDto target = targets.get(index);
            if (target != null && target.modelId() != null) {
                originalIndexByModelId.put(target.modelId(), index);
            }
        }

        List<MarketStatsTargetDto> batch = MarketStatsTargetRotation.batch(
                targets,
                normalizedStartIndex,
                maxTargetsPerBatch
        );

        returnedTargetCount = batch.size();
        return batch;
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

    int targetCount() {
        return targetCount;
    }

    int returnedTargetCount() {
        return returnedTargetCount;
    }

    List<Long> targetOrderModelIds() {
        return targetOrderModelIds;
    }

    static List<MarketStatsTargetDto> applyPreferredTargetOrder(
            List<MarketStatsTargetDto> targets,
            List<Long> preferredTargetOrder
    ) {
        if (targets == null || targets.isEmpty()) {
            return List.of();
        }

        if (preferredTargetOrder == null || preferredTargetOrder.isEmpty()) {
            return List.copyOf(targets);
        }

        Map<Long, MarketStatsTargetDto> byModelId =
                new LinkedHashMap<>();
        List<MarketStatsTargetDto> withoutModelId =
                new ArrayList<>();

        for (MarketStatsTargetDto target : targets) {
            if (target == null || target.modelId() == null) {
                withoutModelId.add(target);
                continue;
            }
            byModelId.putIfAbsent(target.modelId(), target);
        }

        List<MarketStatsTargetDto> ordered =
                new ArrayList<>(targets.size());

        for (Long modelId : preferredTargetOrder) {
            MarketStatsTargetDto target = byModelId.remove(modelId);
            if (target != null) {
                ordered.add(target);
            }
        }

        ordered.addAll(byModelId.values());
        ordered.addAll(withoutModelId);

        return List.copyOf(ordered);
    }
}
