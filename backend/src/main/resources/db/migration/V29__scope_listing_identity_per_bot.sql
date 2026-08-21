-- A Vinted marketplace item may legitimately be observed by multiple FlipBot
-- configurations/accounts.  Marketplace listing_id is therefore unique only
-- inside one bot, not globally across the whole application.
--
-- Older installations may have received either a generated UNIQUE constraint
-- or a standalone UNIQUE index for listing_id, so remove both forms safely.

DO $$
DECLARE
    constraint_row record;
    index_row record;
BEGIN
    FOR constraint_row IN
        SELECT con.conname
        FROM pg_constraint con
        JOIN pg_class rel
          ON rel.oid = con.conrelid
        JOIN pg_namespace nsp
          ON nsp.oid = rel.relnamespace
        WHERE nsp.nspname = current_schema()
          AND rel.relname = 'listing'
          AND con.contype = 'u'
          AND ARRAY(
                SELECT att.attname::text
                FROM unnest(con.conkey) WITH ORDINALITY AS key_cols(attnum, ordinality)
                JOIN pg_attribute att
                  ON att.attrelid = rel.oid
                 AND att.attnum = key_cols.attnum
                ORDER BY key_cols.ordinality
              ) = ARRAY['listing_id']::text[]
    LOOP
        EXECUTE format(
                'ALTER TABLE %I.listing DROP CONSTRAINT %I',
                current_schema(),
                constraint_row.conname
        );
    END LOOP;

    FOR index_row IN
        SELECT idx.relname AS index_name
        FROM pg_index ind
        JOIN pg_class rel
          ON rel.oid = ind.indrelid
        JOIN pg_namespace nsp
          ON nsp.oid = rel.relnamespace
        JOIN pg_class idx
          ON idx.oid = ind.indexrelid
        WHERE nsp.nspname = current_schema()
          AND rel.relname = 'listing'
          AND ind.indisunique
          AND NOT ind.indisprimary
          AND NOT EXISTS (
                SELECT 1
                FROM pg_constraint con
                WHERE con.conindid = ind.indexrelid
          )
          AND ARRAY(
                SELECT att.attname::text
                FROM unnest(ind.indkey::smallint[]) WITH ORDINALITY AS key_cols(attnum, ordinality)
                JOIN pg_attribute att
                  ON att.attrelid = rel.oid
                 AND att.attnum = key_cols.attnum
                WHERE key_cols.attnum > 0
                ORDER BY key_cols.ordinality
              ) = ARRAY['listing_id']::text[]
    LOOP
        EXECUTE format(
                'DROP INDEX IF EXISTS %I.%I',
                current_schema(),
                index_row.index_name
        );
    END LOOP;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint con
        JOIN pg_class rel
          ON rel.oid = con.conrelid
        JOIN pg_namespace nsp
          ON nsp.oid = rel.relnamespace
        WHERE nsp.nspname = current_schema()
          AND rel.relname = 'listing'
          AND con.conname = 'uk_listing_bot_marketplace'
    ) THEN
        ALTER TABLE listing
            ADD CONSTRAINT uk_listing_bot_marketplace
            UNIQUE (bot_id, listing_id);
    END IF;
END $$;
