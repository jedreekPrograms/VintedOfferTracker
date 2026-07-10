package pl.flipbot.negotiation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NegotiationStepRepository extends JpaRepository<NegotiationStep, Long> {
    List<NegotiationStep> findByConfigurationIdOrderByStepNumber(Long configurationId);
}
