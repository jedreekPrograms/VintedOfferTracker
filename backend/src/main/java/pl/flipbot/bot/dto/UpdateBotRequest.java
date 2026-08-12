package pl.flipbot.bot.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateBotRequest {

    @NotBlank
    private String name;

    @NotBlank
    private String email;

    /*
     * Null albo pusty string oznacza:
     * pozostaw aktualne hasło bez zmian.
     *
     * Dzięki temu frontend nie musi nigdy pobierać
     * odszyfrowanego hasła z backendu.
     */
    private String password;

    @Valid
    @NotNull
    private CreateBotConfigurationRequest configuration;
}