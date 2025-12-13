create table if not exists element_xml
(
    sql_id       varchar  not null ,
    file_path    varchar not null,
    tag_name     varchar not null,
    database_id  varchar not null,
    start_offset integer       not null,
    end_offset   integer       not null,
    index        element_xml_sql_id (sql_id),
    index        element_xml_file_path (sql_id),
    index        element_xml_tag_name (tag_name),
    index        element_xml_database_id (database_id)
)
