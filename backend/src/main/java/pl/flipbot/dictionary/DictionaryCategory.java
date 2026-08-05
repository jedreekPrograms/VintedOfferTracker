package pl.flipbot.dictionary;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "dictionary_category",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_dictionary_category_path",
                        columnNames = "path"
                )
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DictionaryCategory {

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

    @Column(
            nullable = false,
            length = 1000
    )
    private String path;
    
}
