package pl.flipbot.bot;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BotRepository extends JpaRepository<Bot, Long> {

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(
            String email,
            Long id
    );

    List<Bot> findByStatus(
            BotStatus status
    );

    List<Bot> findAllByMarketStatsObserverFalseOrderByIdAsc();

    List<Bot> findByStatusAndMarketStatsObserverFalse(
            BotStatus status
    );

    Optional<Bot> findFirstByMarketStatsObserverTrue();
}
