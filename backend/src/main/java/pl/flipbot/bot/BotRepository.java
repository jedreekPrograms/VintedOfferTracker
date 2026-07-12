package pl.flipbot.bot;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BotRepository extends JpaRepository<Bot, Long> {

    boolean existsByEmail(String email);

    List<Bot> findByStatus(BotStatus status);
}
