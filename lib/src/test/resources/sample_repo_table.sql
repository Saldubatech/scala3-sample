CREATE TABLE IF NOT EXISTS sample (
    record_id VARCHAR PRIMARY KEY NOT NULL,
    entity_id VARCHAR NOT NULL,
    recorded_at bigint not NULL,
    effective_at bigint not null,
    something_or_other VARCHAR NOT NULL
);
