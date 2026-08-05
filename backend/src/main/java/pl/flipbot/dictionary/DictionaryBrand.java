package pl.flipbot.dictionary;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name="dictionary_brand",
        uniqueConstraints = {
                @UniqueConstraint(
                        name="uk_dictionary_brand_name",
                        columnNames="name"
                )
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DictionaryBrand {

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

}
