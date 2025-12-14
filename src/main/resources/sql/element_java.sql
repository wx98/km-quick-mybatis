create table if not exists element_java
(
    sql_id       character varying not null,
    file_path    character varying not null,
    element_type character varying not null,
    start_offset integer           not null,
    end_offset   integer           not null,
    index element_java_file_path (file_path),
    index element_java_element_type (element_type),
    UNIQUE INDEX idx_unique_java_element (sql_id, file_path, element_type, start_offset)
)

