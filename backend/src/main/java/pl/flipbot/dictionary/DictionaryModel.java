package pl.flipbot.dictionary;

import jakarta.persistence.*;
import lombok.*;

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
}
