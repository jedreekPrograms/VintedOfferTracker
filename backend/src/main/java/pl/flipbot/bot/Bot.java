package pl.flipbot.bot;

import jakarta.persistence.*;
import lombok.*;
import pl.flipbot.category.Category;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Bot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String email;

    private String password;

    @Enumerated(EnumType.STRING)
    private BotStatus status;

    @ManyToOne
    private Category category;
}
