package pl.flipbot.analytics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import pl.flipbot.dictionary.DictionaryBrand;
import pl.flipbot.dictionary.DictionaryModel;
import pl.flipbot.listing.Listing;
import pl.flipbot.listing.ListingHistoryMetadata;

import java.util.List;

/**
 * One-way compatibility backfill for historical buy-candidates created before
 * productTargetLabel snapshots were persisted.
 *
 * It only fills a NULL product_target_label after HistoryModelResolver can
 * resolve one unique model against the complete dictionary. Existing labels
 * are never overwritten.
 */
@Slf4j
@Component
@Order(250)
@RequiredArgsConstructor
public class HistoryModelBackfillInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final HistoryModelResolver historyModelResolver;

    @Override
    public void run(ApplicationArguments args) {
        List<DictionaryModel> models = jdbcTemplate.query(
                """
                SELECT
                    m.id AS model_id,
                    m.name AS model_name,
                    b.id AS brand_id,
                    b.name AS brand_name
                FROM dictionary_model m
                JOIN dictionary_brand b
                  ON b.id = m.brand_id
                ORDER BY m.id
                """,
                (resultSet, rowNumber) ->
                        DictionaryModel.builder()
                                .id(resultSet.getLong("model_id"))
                                .name(resultSet.getString("model_name"))
                                .brand(
                                        DictionaryBrand.builder()
                                                .id(resultSet.getLong("brand_id"))
                                                .name(resultSet.getString("brand_name"))
                                                .build()
                                )
                                .build()
        );

        if (models.isEmpty()) {
            log.info(
                    "[HISTORY MODEL BACKFILL] Dictionary is empty; no historical labels to resolve."
            );
            return;
        }

        List<LegacyListing> legacyListings = jdbcTemplate.query(
                """
                SELECT id, title
                FROM listing
                WHERE buy_candidate_at IS NOT NULL
                  AND product_target_label IS NULL
                ORDER BY id
                """,
                (resultSet, rowNumber) ->
                        new LegacyListing(
                                resultSet.getLong("id"),
                                resultSet.getString("title")
                        )
        );

        int resolved = 0;
        int unresolved = 0;

        for (LegacyListing legacy : legacyListings) {
            Listing listing = Listing.builder()
                    .id(legacy.id())
                    .title(legacy.title())
                    .build();

            var model = historyModelResolver.resolveModel(
                    listing,
                    models
            );

            if (model.isEmpty()) {
                unresolved++;
                continue;
            }

            DictionaryModel resolvedModel = model.get();
            String label = ListingHistoryMetadata.modelLabel(
                    resolvedModel.getBrand().getName(),
                    resolvedModel.getName()
            );

            int updated = jdbcTemplate.update(
                    """
                    UPDATE listing
                    SET product_target_label = ?
                    WHERE id = ?
                      AND product_target_label IS NULL
                    """,
                    label,
                    legacy.id()
            );

            resolved += updated;
        }

        log.info(
                "[HISTORY MODEL BACKFILL] Checked {} legacy buy-candidates without a model snapshot; resolved={}, unresolved={}. Existing snapshots were left untouched.",
                legacyListings.size(),
                resolved,
                unresolved
        );
    }

    private record LegacyListing(
            Long id,
            String title
    ) {
    }
}
