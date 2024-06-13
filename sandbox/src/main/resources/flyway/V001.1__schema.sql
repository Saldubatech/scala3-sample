create table if not exists event_record(
  rid varchar(255) primary key not null,
  batch varchar(255) not null,
  op_type varchar(255) not null,
  at bigint not null,
  job varchar(255) not null,
  station varchar(255) not null,
  from_station varchar(255) not null
);

create table if not exists operation_record (
  rid varchar(255) primary key not null,
  batch varchar(255) not null,
  operation varchar(255) not null,
  started bigint not null,
  job varchar(255) not null,
  station varchar(255) not null
);
