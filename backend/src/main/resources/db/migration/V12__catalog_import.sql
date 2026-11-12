-- Rows a catalog import skipped, with the reason. Counts live in Spring Batch's own tables.
CREATE TABLE catalog_import_error (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    job_execution_id BIGINT       NOT NULL,
    line_number      INT          NOT NULL,
    message          VARCHAR(500) NOT NULL
);

CREATE INDEX catalog_import_error_job_idx ON catalog_import_error (job_execution_id);
