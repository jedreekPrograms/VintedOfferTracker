package pl.flipbot.bot.configuration;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.flipbot.negotiation.NegotiationStep;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "bot_additional_target")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BotAdditionalTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "configuration_id", nullable = false)
    private BotConfiguration configuration;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "bot_additional_target_category_path",
            joinColumns = @JoinColumn(name = "target_id")
    )
    @Column(name = "category", nullable = false)
    @OrderColumn(name = "path_index")
    @Builder.Default
    private List<String> categoryPath = new ArrayList<>();

    @Column(nullable = false)
    private String brand;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_mode", nullable = false)
    @Builder.Default
    private TargetMode targetMode = TargetMode.VINTED_MODEL;

    private String model;

    @Column(name = "search_query")
    private String searchQuery;

    @Column(name = "min_price", nullable = false)
    private BigDecimal minPrice;

    @Column(name = "max_price", nullable = false)
    private BigDecimal maxPrice;

    @Column(name = "auto_raise_offer_to_vinted_minimum", nullable = false)
    @Builder.Default
    private Boolean autoRaiseOfferToVintedMinimum = false;

    @Column(name = "max_automatic_offer")
    private BigDecimal maxAutomaticOffer;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @OneToMany(
            mappedBy = "additionalTarget",
            cascade = jakarta.persistence.CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.EAGER
    )
    @Builder.Default
    private List<NegotiationStep> negotiationSteps = new ArrayList<>();
}
