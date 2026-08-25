package pl.flipbot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    /**
     * FlipBot currently has no end-user identity model. The backend is therefore
     * intentionally bound to loopback by default (see application.yml) and the
     * dashboard/Playwright runtime communicate with it locally.
     *
     * Do not turn this into a remotely reachable API without introducing a real
     * authentication/authorization boundary first: several internal endpoints
     * intentionally carry bot credentials or authorize real marketplace actions.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll()
                );

        return http.build();
    }
}
