package pl.flipbot.bot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BotRepository extends JpaRepository<Bot, Long> {

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(
            String email,
            Long id
    );

    @Override
    @Query("""
            select bot
            from Bot bot
            where bot.marketStatsObserver = false
            order by bot.id asc
            """)
    List<Bot> findAll();

    @Override
    @Query("""
            select bot
            from Bot bot
            where bot.id = :id
              and bot.marketStatsObserver = false
            """)
    Optional<Bot> findById(
            @Param("id") Long id
    );

    @Query("""
            select bot
            from Bot bot
            where bot.status = :status
              and bot.marketStatsObserver = false
            """)
    List<Bot> findByStatus(
            @Param("status") BotStatus status
    );

    Optional<Bot> findFirstByMarketStatsObserverTrue();

    Optional<Bot> findByIdAndMarketStatsObserverTrue(
            Long id
    );
}
