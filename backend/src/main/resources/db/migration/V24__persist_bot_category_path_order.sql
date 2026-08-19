-- BotConfiguration.categoryPath is a hierarchy, so element order is semantic:
-- e.g. Elektronika -> Telefony. The old @ElementCollection had no @OrderColumn,
-- which meant PostgreSQL/JPA were free to return the same rows in a different
-- order on a later request. That could make a no-op edit look like a category
-- change while active negotiations existed and could also feed Playwright an
-- incorrectly ordered navigation path.
--
-- Reconstruct the canonical order from dictionary_category.path, then persist
-- it explicitly for every existing bot configuration.

ALTER TABLE bot_category_path
    ADD COLUMN IF NOT EXISTS path_index integer;

WITH configuration_categories AS (
    SELECT
        configuration_id,
        array_agg(
            lower(
                regexp_replace(
                    btrim(category),
                    '[[:space:]]+',
                    ' ',
                    'g'
                )
            )
            ORDER BY lower(
                regexp_replace(
                    btrim(category),
                    '[[:space:]]+',
                    ' ',
                    'g'
                )
            )
        ) AS normalized_categories,
        count(*) AS category_count
    FROM bot_category_path
    GROUP BY configuration_id
),
dictionary_paths AS (
    SELECT
        id,
        regexp_split_to_array(
            path,
            '[[:space:]]*>[[:space:]]*'
        ) AS parts
    FROM dictionary_category
),
matching_paths AS (
    SELECT
        configuration_categories.configuration_id,
        dictionary_paths.parts
    FROM configuration_categories
    JOIN dictionary_paths
      ON cardinality(dictionary_paths.parts)
         = configuration_categories.category_count
    WHERE ARRAY(
        SELECT lower(
            regexp_replace(
                btrim(part),
                '[[:space:]]+',
                ' ',
                'g'
            )
        )
        FROM unnest(dictionary_paths.parts) AS part
        ORDER BY 1
    ) = configuration_categories.normalized_categories
),
resolved_positions AS (
    SELECT
        bot_category_path.ctid AS row_id,
        (path_part.ordinality - 1)::integer AS path_index
    FROM bot_category_path
    JOIN matching_paths
      ON matching_paths.configuration_id
         = bot_category_path.configuration_id
    CROSS JOIN LATERAL unnest(matching_paths.parts)
        WITH ORDINALITY AS path_part(category, ordinality)
    WHERE lower(
        regexp_replace(
            btrim(bot_category_path.category),
            '[[:space:]]+',
            ' ',
            'g'
        )
    ) = lower(
        regexp_replace(
            btrim(path_part.category),
            '[[:space:]]+',
            ' ',
            'g'
        )
    )
)
UPDATE bot_category_path
SET path_index = resolved_positions.path_index
FROM resolved_positions
WHERE bot_category_path.ctid = resolved_positions.row_id;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM bot_category_path
        WHERE path_index IS NULL
    ) THEN
        RAISE EXCEPTION
            'Could not reconstruct category path order for every bot configuration. Migration aborted to avoid silently corrupting category navigation.';
    END IF;
END
$$;

ALTER TABLE bot_category_path
    ALTER COLUMN path_index SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_bot_category_path_position
    ON bot_category_path(configuration_id, path_index);
