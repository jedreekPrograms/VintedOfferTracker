package pl.flipbot.marketstats;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.flipbot.dictionary.DictionaryModelRepository;

@Service
@RequiredArgsConstructor
public class MarketStatsScanTriggerService {

    private final DictionaryModelRepository modelRepository;
    private final MarketModelScanStateRepository scanStateRepository;

    @Transactional(readOnly = true)
    public boolean isScanNeeded() {
        long modelCount = modelRepository.count();

        if (modelCount == 0) {
            return false;
        }

        long completedBaselines =
                scanStateRepository.countByBaselineCompleteAtIsNotNull();

        return completedBaselines < modelCount;
    }
}
