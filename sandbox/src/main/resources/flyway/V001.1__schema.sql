create table if not exists event_record(
  rid varchar(255) primary key not null,
  batch varchar(255) not null,
  op_type varchar(255) not null,
  at bigint not null,
  job varchar(255) not null,
  station varchar(255) not null,
  from_station varchar(255) not null,
  real_time bigint not null
);

create table if not exists operation_record (
  rid varchar(255) primary key not null,
  batch varchar(255) not null,
  operation varchar(255) not null,
  job varchar(255) not null,
  start_station varchar(255) not null,
  start_event varchar(255) not null references event_record(rid),
  started bigint not null,
  end_station varchar(255) not null,
  end_event varchar(255) not null references event_record(rid),
  ended bigint not null,
  duration bigint not null
);
