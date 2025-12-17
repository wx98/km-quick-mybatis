create table if not exists element_xml
(
    sql_id       character varying not null,
    file_path    character varying not null,
    tag_name     character varying not null,
    database_id  character varying not null,
    start_offset integer           not null,
    end_offset   integer           not null,
    index element_xml_file_path (file_path),
    index element_xml_tag_name (tag_name),
    index element_xml_database_id (database_id),
    UNIQUE INDEX idx_unique_xml_element (sql_id, file_path, tag_name, database_id, start_offset)
)
