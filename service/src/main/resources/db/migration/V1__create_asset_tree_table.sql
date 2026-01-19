CREATE EXTENSION IF NOT EXISTS ltree;

CREATE TABLE IF NOT EXISTS asset_tree
(
    id          UUID       NOT NULL PRIMARY KEY,
    entry_id    BIGINT     NOT NULL,
    path        ltree      NOT NULL,
    alt         TEXT,
    source      TEXT       NOT NULL,
    source_url  TEXT,
    is_ready    BOOLEAN    NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP  WITHOUT TIME ZONE NOT NULL,
    modified_at TIMESTAMP  WITHOUT TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS asset_tree_path_gist_idx ON asset_tree USING gist (path);
CREATE INDEX IF NOT EXISTS asset_tree_path_is_ready_idx ON asset_tree (path, is_ready);
CREATE UNIQUE INDEX IF NOT EXISTS asset_tree_path_entry_id_uq ON asset_tree (path, entry_id);
CREATE INDEX IF NOT EXISTS asset_tree_not_ready ON asset_tree (id) WHERE is_ready IS FALSE;

CREATE TABLE IF NOT EXISTS asset_variant
(
    id                  UUID                        NOT NULL PRIMARY KEY,
    asset_id            UUID                        NOT NULL,
    object_store_bucket TEXT                        NOT NULL,
    object_store_key    TEXT                        NOT NULL,
    transformation      JSONB                       NOT NULL,
    attributes          JSONB                       NOT NULL,
    lqip                JSONB                       NOT NULL,
    original_variant    BOOLEAN                     NOT NULL,
    created_at          TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    uploaded_at         TIMESTAMP WITHOUT TIME ZONE,
    -- No ON DELETE CASCADE intentionally
    CONSTRAINT fk_asset_variant_asset_id_asset_tree_id FOREIGN KEY (asset_id)
        REFERENCES asset_tree(id)
);

CREATE INDEX IF NOT EXISTS asset_variant_asset_id_idx ON asset_variant (asset_id);
CREATE UNIQUE INDEX IF NOT EXISTS asset_variant_transformation_uq ON asset_variant (asset_id, transformation);
CREATE UNIQUE INDEX IF NOT EXISTS asset_variant_asset_id_original_variant_uq
    ON asset_variant (asset_id)
    WHERE (original_variant = true);
CREATE INDEX IF NOT EXISTS asset_variant_not_uploaded ON asset_variant (id) WHERE uploaded_at IS NULL;