-- Search read model, owned by the search module and rebuilt from the catalog.
-- Embedding size matches the default embedding model (nomic-embed-text, 768 dimensions).
CREATE TABLE search_document (
    product_id BIGINT PRIMARY KEY,
    category   VARCHAR(80)   NOT NULL,
    price      NUMERIC(12,2) NOT NULL,
    in_stock   BOOLEAN       NOT NULL,
    document   TEXT          NOT NULL,
    tsv        TSVECTOR GENERATED ALWAYS AS (to_tsvector('english', document)) STORED,
    embedding  VECTOR(768),
    indexed_at TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX search_document_tsv_idx ON search_document USING GIN (tsv);
CREATE INDEX search_document_embedding_idx ON search_document USING hnsw (embedding vector_cosine_ops);
