package pl.flipbot.bot.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class BotAdditionalTargetSchemaInitializerTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void targetOwnedRowsCascadeWhenAdditionalProductIsDeleted() {
        assertCascade("fk_negotiation_step_additional_target");
        assertCascade("fk_listing_additional_target");
    }

    @Test
    void listingProductProvenanceColumnExistsForExistingDatabaseCompatibility() {
        Integer maximumLength = jdbcTemplate.queryForObject(
                """
                SELECT character_maximum_length
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = 'listing'
                  AND column_name = 'product_target_label'
                """,
                Integer.class
        );

        assertEquals(1000, maximumLength);
    }

    private void assertCascade(String constraintName) {
        String deleteAction = jdbcTemplate.queryForObject(
                """
                SELECT confdeltype::text
                FROM pg_constraint
                WHERE conname = ?
                """,
                String.class,
                constraintName
        );

        assertEquals(
                "c",
                deleteAction,
                constraintName + " must use ON DELETE CASCADE"
        );
    }
}
