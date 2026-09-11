package pl.flipbot.marketstats;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MarketStatsScanCompletenessService {

    private final MarketModelScanStateRepository scanStateRepository;

    @Transactional(readOnly = true)
    public boolean requiresFullCatalogScan(Long modelId) {
        if (modelId == null || modelId <= 0) {
            return true;
        }

        return scanStateRepository.findById(modelId)
                .map(state -> state.getBaselineCompleteAt() == null
                        || !Boolean.TRUE.equals(state.getLastScanComplete()))
                .orElse(true);
    }
}
