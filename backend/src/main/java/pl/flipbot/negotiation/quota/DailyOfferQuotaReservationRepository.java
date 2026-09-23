package pl.flipbot.negotiation.quota;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface DailyOfferQuotaReservationRepository
        extends JpaRepository<DailyOfferQuotaReservation, UUID> {

    List<DailyOfferQuotaReservation> findAllByBotIdAndUsageDateAndActiveTrue(
            Long botId,
            LocalDate usageDate
    );
}
