package pl.flipbot.negotiation.quota;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyOfferQuotaRepository
        extends JpaRepository<DailyOfferQuota, Long> {

    Optional<DailyOfferQuota>
    findByBot_IdAndUsageDate(
            Long botId,
            LocalDate usageDate
    );

    List<DailyOfferQuota> findAllByUsageDateGreaterThanEqual(
            LocalDate usageDate
    );
}
