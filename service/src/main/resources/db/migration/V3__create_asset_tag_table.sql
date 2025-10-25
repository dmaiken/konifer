CREATE TABLE IF NOT EXISTS asset_tag
(
    id UUID NOT NULL PRIMARY KEY,
    asset_id UUID NOT NULL,
    tag_value TEXT NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT fk_asset_tag_asset_id_asset_tree_id FOREIGN KEY (asset_id)
        REFERENCES asset_tree(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS asset_tag_asset_id_idx ON asset_tag (asset_id);
CREATE UNIQUE INDEX IF NOT EXISTS asset_tag_key_value_uq ON asset_tag (asset_id, tag_value);
