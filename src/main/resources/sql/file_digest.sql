create table if not exists file_digest
(
    file_path varchar not null,
    digest    char(8) not null,
    index     file_digest_file_path (file_path)
)