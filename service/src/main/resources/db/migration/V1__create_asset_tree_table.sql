CREATE EXTENSION IF NOT EXISTS ltree;

CREATE TABLE IF NOT EXISTS asset_tree
(
    id         UUID      NOT NULL PRIMARY KEY,
    entry_id BIGINT NOT NULL,
    path       ltree     NOT NULL,
    alt        TEXT,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS asset_path_idx ON asset_tree USING gist (path);

CREATE TABLE IF NOT EXISTS asset_variant
(
    id                  UUID                        NOT NULL PRIMARY KEY,
    asset_id            UUID                        NOT NULL,
    object_store_bucket TEXT                        NOT NULL,
    object_store_key    TEXT                        NOT NULL,
    transformation          JSONB                       NOT NULL,
    transformation_key      BIGINT                      NOT NULL,
    attributes          JSONB                       NOT NULL,
    lqip                JSONB                       NOT NULL,
    original_variant    BOOLEAN                     NOT NULL,
    created_at          TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT fk_asset_variant_asset_id_asset_tree_id FOREIGN KEY (asset_id)
        REFERENCES asset_tree(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS asset_variant_asset_id_idx ON asset_variant (asset_id);
CREATE INDEX IF NOT EXISTS asset_variant_transformation_key ON asset_variant (transformation_key);
CREATE UNIQUE INDEX IF NOT EXISTS asset_variant_transformation_uq ON asset_variant (asset_id, transformation_key);
CREATE UNIQUE INDEX IF NOT EXISTS asset_variant_asset_id_original_variant_uq ON asset_variant (asset_id, original_variant);
