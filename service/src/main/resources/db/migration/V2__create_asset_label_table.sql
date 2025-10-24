CREATE TABLE IF NOT EXISTS asset_label
(
    id UUID NOT NULL PRIMARY KEY,
    asset_id UUID NOT NULL,
    label_key TEXT NOT NULL,
    label_value TEXT NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT fk_asset_label_asset_id_asset_tree_id FOREIGN KEY (asset_id)
        REFERENCES asset_tree(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS asset_label_asset_id_idx ON asset_label (asset_id);
CREATE UNIQUE INDEX IF NOT EXISTS asset_label_key_value_uq ON asset_label (asset_id, label_key, label_value);
