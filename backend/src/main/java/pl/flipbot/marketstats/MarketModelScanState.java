package pl.flipbot.marketstats;

import jakarta.persistence.*;
import lombok.*;
import pl.flipbot.dictionary.DictionaryModel;

import java.time.LocalDateTime;

@Entity
@Table(name = "market_model_scan_state")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketModelScanState {

    @Id
    @Column(name = "model_id")
    private Long modelId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "model_id")
    private DictionaryModel model;

    @Column(name = "initialized_at", nullable = false)
    private LocalDateTime initializedAt;

    @Column(name = "baseline_complete_at")
    private LocalDateTime baselineCompleteAt;

    @Column(name = "last_scan_at", nullable = false)
    private LocalDateTime lastScanAt;

    @Column(name = "last_successful_scan_at")
    private LocalDateTime lastSuccessfulScanAt;

    @Column(name = "last_scan_complete", nullable = false)
    private Boolean lastScanComplete;
}
