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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.flipbot.bot.Bot;
import pl.flipbot.marketplace.Marketplace;
import pl.flipbot.negotiation.NegotiationStep;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BotConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private Marketplace marketplace;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "bot_category_path",
            joinColumns = @JoinColumn(name = "configuration_id")
    )
    @Column(name = "category")
    @Builder.Default
    private List<String> categoryPath = new ArrayList<>();

    private String brand;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private TargetMode targetMode = TargetMode.VINTED_MODEL;

    /*
     * Używane dla TargetMode.VINTED_MODEL.
     *
     * Przykład:
     * Galaxy S25
     */
    private String model;

    /*
     * Używane dla TargetMode.SEARCH_QUERY.
     *
     * Przykład:
     * Galaxy Tab S11 Ultra
     */
    private String searchQuery;

    private BigDecimal minPrice;

    private BigDecimal maxPrice;

    /*
     * Jeżeli true, kwoty z negotiationSteps są bazową drabinką cenową.
     *
     * Gdy pierwszy skonfigurowany krok jest za niski dla Vinted, Playwright
     * podnosi pierwszą ofertę do bezpiecznej wartości i skaluje kolejne kroki
     * proporcjonalnie do różnic pomiędzy kwotami zapisanymi w konfiguracji.
     */
    @Builder.Default
    private Boolean autoRaiseOfferToVintedMinimum = false;

    /*
     * Historyczna nazwa pola pozostaje dla zgodności API i bazy.
     * W trybie adaptacyjnym jest to globalny limit automatycznej negocjacji:
     * żaden kolejny automatyczny krok ani próg akceptowanej kontroferty nie
     * może zostać wyliczony powyżej tej kwoty.
     */
    private BigDecimal maxAutomaticOffer;

    private Integer dailyNegotiationBudget;

    @OneToOne
    @JoinColumn(name = "bot_id")
    private Bot bot;

    @OneToMany(
            mappedBy = "configuration",
            cascade = jakarta.persistence.CascadeType.ALL,
            orphanRemoval = true
    )
    @Builder.Default
    private List<NegotiationStep> negotiationSteps = new ArrayList<>();
}
