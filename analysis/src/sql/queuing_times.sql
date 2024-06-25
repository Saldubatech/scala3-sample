select distinct job from event_record;

-- delete from event_record;

-- E2E Lead Time.
select n.batch, n.job, n.at, c.at - n.at as e2e_lead_time from
  event_record as n
  join event_record as c on n.job = c.job and n.batch = c.batch
where n.op_type = 'NEW' and c.op_type = 'COMPLETE';

-- New to Depart (should be zero for the case of immediate processing)
select n.job, d.station, n.at, d.at - n.at as receiving_time from
  event_record as n
  join event_record as d on n.job = d.job and n.batch = d.batch and n.station = d.station
where n.op_type = 'NEW' and d.op_type = 'DEPART';

-- Depart to Arrive (shoudl be zero for this case)
select d.job, d.station as from_station, a.station as to_station, d.at, a.at - d.at as transport_time from
  event_record as d
  join event_record as a on a.job = d.job and a.batch = d.batch and d.station = a.from_station
where d.op_type = 'DEPART' and a.op_type = 'ARRIVE';


-- Arrive to Start
select a.job, a.station, a.at, s.at - a.at as wait_time from
  event_record as s
  join event_record as a on s.job = a.job and s.batch = a.batch and s.station = a.station
where s.op_type = 'START' and a.op_type = 'ARRIVE';

-- Start to End
select e.job, e.station, s.at, e.at - s.at as processing_time from
  event_record as e
  join event_record as s on s.job = e.job and s.batch = e.batch and s.station = e.station
where s.op_type = 'START' and e.op_type = 'END' order by s.at;

-- End to Depart
select d.job, d.station, e.at, d.at - e.at as depart_delay from
  event_record as d
  join event_record as e on d.job = e.job and d.batch = d.batch and d.station = e.station
where d.op_type = 'DEPART' and e.op_type = 'END';
