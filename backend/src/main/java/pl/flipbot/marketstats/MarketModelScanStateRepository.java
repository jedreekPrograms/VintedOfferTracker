package pl.flipbot.marketstats;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface MarketModelScanStateRepository
        extends JpaRepository<MarketModelScanState, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MarketModelScanState> findByModelId(Long modelId);
}
