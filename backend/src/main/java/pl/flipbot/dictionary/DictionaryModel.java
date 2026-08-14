package pl.flipbot.dictionary;

import jakarta.persistence.*;
import lombok.*;
import pl.flipbot.bot.configuration.TargetMode;

import java.math.BigDecimal;

@Entity
@Table(
        name = "dictionary_model",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_dictionary_model_brand_name",
                        columnNames = {
                                "brand_id",
                                "name"
                        }
                )
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DictionaryModel {

    @Id
    @GeneratedValue(
            strategy = GenerationType.IDENTITY
    )
    private Long id;

    @Column(
            nullable = false,
            length = 255
    )
    private String name;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "brand_id",
            nullable = false
    )
    private DictionaryBrand brand;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "target_mode",
            nullable = false,
            length = 32
    )
    @Builder.Default
    private TargetMode targetMode =
            TargetMode.VINTED_MODEL;

    @Column(
            name = "proposed_offer_price",
            precision = 38,
            scale = 2
    )
    private BigDecimal proposedOfferPrice;

    @Column(
            name = "expected_resale_price",
            precision = 38,
            scale = 2
    )
    private BigDecimal expectedResalePrice;
}
