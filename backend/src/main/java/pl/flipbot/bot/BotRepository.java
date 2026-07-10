package pl.flipbot.bot;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BotRepository extends JpaRepository<Bot, Long> {

    boolean existsByEmail(String email);
}
