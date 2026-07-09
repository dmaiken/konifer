CREATE TABLE IF NOT EXISTS prompt_embedding
(
    id UUID NOT NULL PRIMARY KEY,
    model TEXT NOT NULL,
    prompt_text TEXT NOT NULL,
    embedding REAL[] NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS prompt_embedding_cache_uq
    ON prompt_embedding (model, prompt_text);
