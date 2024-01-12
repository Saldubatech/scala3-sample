CREATE TABLE IF NOT EXISTS items(
  recordid varchar primary key not null,
  entityid varchar not null,
  recordedat bigint NOT NULL,
  effectiveat bigint not null,
  name VARCHAR NOT NULL,
  price NUMERIC(21, 2) NOT NULL
);
