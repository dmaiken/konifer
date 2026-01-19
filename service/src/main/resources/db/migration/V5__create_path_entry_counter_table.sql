CREATE TABLE IF NOT EXISTS path_entry_counter (
    path ltree PRIMARY KEY,
    last_entry_id BIGINT NOT NULL DEFAULT 0
);

-- Atomically assign a new, incremented entry_id to the row being inserted
CREATE OR REPLACE FUNCTION fn_assign_entry_id()
    RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO path_entry_counter (path, last_entry_id)
    VALUES (NEW.path, 0)
    ON CONFLICT (path)
        DO UPDATE SET last_entry_id = path_entry_counter.last_entry_id + 1
    RETURNING last_entry_id INTO NEW.entry_id;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_assign_entry_id
    BEFORE INSERT ON asset_tree
    FOR EACH ROW
EXECUTE FUNCTION fn_assign_entry_id();
