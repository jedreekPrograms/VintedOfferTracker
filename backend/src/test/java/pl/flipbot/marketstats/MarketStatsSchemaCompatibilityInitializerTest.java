package pl.flipbot.marketstats;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class MarketStatsSchemaCompatibilityInitializerTest {

    @Test
    void appliesOnlyTheNarrowCompatibilityStatements() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        MarketStatsSchemaCompatibilityInitializer initializer =
                new MarketStatsSchemaCompatibilityInitializer(jdbcTemplate);

        initializer.run(new DefaultApplicationArguments(new String[0]));

        verify(jdbcTemplate, times(4)).execute(anyString());
        verify(jdbcTemplate, times(2)).update(anyString());
    }
}
