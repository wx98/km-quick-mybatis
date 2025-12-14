create table if not exists file_digest
(
    file_path character varying not null,
    digest    character varying not null,
    UNIQUE INDEX idx_unique_file_digest (file_path)
)