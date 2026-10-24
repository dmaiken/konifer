-- Existing application timestamps represent UTC despite their timezone-less type.
ALTER TABLE asset_tree
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN modified_at TYPE TIMESTAMPTZ USING modified_at AT TIME ZONE 'UTC';

ALTER TABLE asset_variant ALTER COLUMN access_score_as_of DROP DEFAULT;
ALTER TABLE asset_variant
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN uploaded_at TYPE TIMESTAMPTZ USING uploaded_at AT TIME ZONE 'UTC',
    ALTER COLUMN expires_at TYPE TIMESTAMPTZ USING expires_at AT TIME ZONE 'UTC',
    ALTER COLUMN last_accessed_at TYPE TIMESTAMPTZ USING last_accessed_at AT TIME ZONE 'UTC',
    ALTER COLUMN access_score_as_of TYPE TIMESTAMPTZ USING access_score_as_of AT TIME ZONE 'UTC';
ALTER TABLE asset_variant ALTER COLUMN access_score_as_of SET DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE asset_label ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';
ALTER TABLE asset_tag ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';
ALTER TABLE outbox ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';
ALTER TABLE prompt_embedding ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';
