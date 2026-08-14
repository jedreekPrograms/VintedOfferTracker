package pl.flipbot.marketstats;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MarketModelScanStateRepository
        extends JpaRepository<MarketModelScanState, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select state
            from MarketModelScanState state
            where state.modelId = :modelId
            """)
    Optional<MarketModelScanState> findByModelIdForUpdate(
            @Param("modelId") Long modelId
    );
}
