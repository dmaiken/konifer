ALTER TABLE IF EXISTS asset_variant ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP WITHOUT TIME ZONE;

CREATE INDEX IF NOT EXISTS asset_variant_expires_at_idx
ON asset_variant (expires_at)
WHERE expires_at IS NOT NULL;
