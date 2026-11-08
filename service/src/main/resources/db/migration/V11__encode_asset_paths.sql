-- A distinct root keeps converted paths disjoint from legacy paths during updates.
-- Existing ltree labels only need underscore escaping.
UPDATE asset_tree
SET path = ('root_v2' || substring(replace(path::text, '_', '__') FROM 5))::ltree;

UPDATE path_entry_counter
SET path = ('root_v2' || substring(replace(path::text, '_', '__') FROM 5))::ltree;

-- Reject writes from older instances after conversion.
ALTER TABLE asset_tree
    ADD CONSTRAINT asset_tree_path_encoding CHECK (path <@ 'root_v2'::ltree);

ALTER TABLE path_entry_counter
    ADD CONSTRAINT path_entry_counter_path_encoding CHECK (path <@ 'root_v2'::ltree);
