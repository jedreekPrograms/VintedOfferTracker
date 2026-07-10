package pl.flipbot.bot;

import jakarta.persistence.*;
import lombok.*;
import pl.flipbot.bot.configuration.BotConfiguration;
import pl.flipbot.listing.Listing;

import java.util.ArrayList;
import java.util.List;

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

    @Column(unique = true)
    private String email;

    private String password;

    @Enumerated(EnumType.STRING)
    private BotStatus status;

    @OneToOne(mappedBy = "bot", cascade = CascadeType.ALL, orphanRemoval = true)
    private BotConfiguration configuration;

    @OneToMany(mappedBy = "bot", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Listing> listings = new ArrayList<>();
}
