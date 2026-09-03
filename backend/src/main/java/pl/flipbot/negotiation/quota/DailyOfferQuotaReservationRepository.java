package pl.flipbot.negotiation.quota;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.UUID;

public interface DailyOfferQuotaReservationRepository
        extends JpaRepository<DailyOfferQuotaReservation, UUID> {

    long countByBotIdAndUsageDateAndActiveTrue(
            Long botId,
            LocalDate usageDate
    );
}
