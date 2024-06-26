from typing import Callable, Optional
import pandas as pd
from matplotlib import pyplot as plt
from matplotlib.axes import Axes
from matplotlib.figure import Figure
from sqlalchemy.engine import Engine


def compute_iat(batchDf: pd.DataFrame) -> pd.DataFrame:
  return pd.DataFrame(
    {
      'job': batchDf['job'],
      'at': batchDf['at'],
      'iat': batchDf['at'].diff(periods=1)
    })


def compute_idt(batchDf: pd.DataFrame) -> pd.DataFrame:
  return pd.DataFrame(
    {
      'job': batchDf['job'],
      'at': batchDf['at'],
      'idt': batchDf['at'].diff(periods=1)
    })


def eventCounter(evName: str) -> Callable[[pd.DataFrame], pd.DataFrame]:
  def counter(batchDf: pd.DataFrame) -> pd.DataFrame:
    ldf = pd.DataFrame(
      data={
        'job': batchDf['job'],
        'at': batchDf['at'],
        'incr': batchDf['op_type'].apply(lambda ot: 1 if ot == evName else -1)
      }
    )
    ldf.loc[:, 'count'] = ldf['incr'].cumsum()
    return ldf
  return counter


def stagePlots(stage: str, timePlot: Axes, wipPlot: Axes, times: pd.Series, wip: pd.Series) -> None:
  timePlot.set_title(f"{stage} Times")
  timePlot.hist(times)
  wipPlot.set_title(f"{stage} Wip")
  wipPlot.hist(wip)
  return


def plotStation(station_name: str, db_engine: Engine, batch: Optional[str] = None) -> None:
  batch_clause = f"batch = 'and {batch}'" if batch is not None else ""
  arrival_events = pd.read_sql_query(
    sql=f"""
    select batch, op_type, at, job, station from event_record
    where station = '{station_name}' and op_type = 'ARRIVE' {batch_clause}
    order by at
    """, con=db_engine)
  departure_events = pd.read_sql_query(
    sql=f"""
    select batch, op_type, at, job, station from event_record
    where station = '{station_name}' and op_type = 'ARRIVE' {batch_clause}
    order by at
    """, con=db_engine)
  end_to_end_times = pd.read_sql_query(
    sql=f"""
    select d.batch, d.job, d.station, a.at, d.at - a.at as end_to_end from
    event_record as d
    join event_record as a on d.job = a.job and d.batch = a.batch and d.station = a.station
    where d.op_type = 'DEPART' and a.op_type = 'ARRIVE' and a.station = '{station_name}' {batch_clause}
    """, con=db_engine)
  end_to_end_wip = pd.read_sql_query(
    sql=f"""
    select batch, job, op_type, at
    from event_record
    where (op_type = 'ARRIVE' or op_type = 'END') and station = '{station_name}'  {batch_clause}
    order by at;
    """, con=db_engine)
  waiting_times = pd.read_sql_query(
    sql=f"""
    select a.batch, a.job, a.station, s.at as start, a.at as arrive, s.at - a.at as wait_time from
    event_record as s
    join event_record as a on s.job = a.job and s.batch = a.batch and s.station = a.station
    where s.op_type = 'START' and a.op_type = 'ARRIVE' and a.station = '{station_name}' {batch_clause}
    """, con=db_engine)
  waiting_events = pd.read_sql_query(
    sql=f"""
    select batch, job, op_type, at
    from event_record
    where (op_type = 'ARRIVE' or op_type = 'START') and station = '{station_name}' {batch_clause}
    order by at;
    """, con=db_engine)
  processing_times = pd.read_sql_query(
    sql=f"""
    select e.job, e.station, s.at, e.at - s.at as processing_time from
    event_record as e
    join event_record as s on s.job = e.job and s.batch = e.batch and s.station = e.station
    where s.op_type = 'START' and e.op_type = 'END' and s.station = '{station_name}' {batch_clause}
    order by s.at;
    """, con=db_engine)
  processing_events = pd.read_sql_query(
    sql=f"""
    select batch, job, op_type, at
    from event_record
    where (op_type = 'START' or op_type = 'END') and station = '{station_name}' {batch_clause}
    order by at;
    """, con=db_engine)
  departure_times = pd.read_sql_query(
    sql=f"""
    select d.job, d.station, e.at, d.at - e.at as depart_delay from
    event_record as d
    join event_record as e on d.job = e.job and d.batch = d.batch and d.station = e.station
    where d.op_type = 'DEPART' and e.op_type = 'END' and e.station = '{station_name}' {batch_clause}
    """, con=db_engine)
  departure_events = pd.read_sql_query(
    sql=f"""
    select batch, job, op_type, at
    from event_record
    where (op_type = 'END' or op_type = 'DEPART') and station = '{station_name}'  {batch_clause}
    order by at;
    """, con=db_engine)

  iat: pd.Series[int] = arrival_events.groupby('batch').apply(compute_iat, include_groups=False)['iat']
  idt: pd.Series[int] = departure_events.groupby('batch').apply(compute_idt, include_groups=False)['idt']

  sojourn: pd.Series[int] = end_to_end_times['end_to_end']
  e2e_wip: pd.Series[int] = end_to_end_wip.groupby('batch').apply(eventCounter('ARRIVE'), include_groups=False)['count']

  wait_time: pd.Series[int] = waiting_times['wait_time']
  waiting_counts: pd.Series[int] = waiting_events.groupby('batch').apply(eventCounter('ARRIVE'), include_groups=False)['count']

  processing_time: pd.Series[int] = processing_times['processing_time']
  processing_counts: pd.Series[int] = processing_events.groupby('batch').apply(eventCounter('START'), include_groups=False)['count']
  #  (with_iat['iat'].mean(), with_iat['iat'].std())

  departure_time: pd.Series[int] = departure_times['depart_delay']
  departure_counts: pd.Series[int] = departure_events.groupby('batch').apply(eventCounter('END'), include_groups=False)['count']

  fig, plots = plt.subplots(5, 2, figsize=(10, 15), gridspec_kw={'height_ratios': [1, 1, 1, 1, 1]})
  print(f"Plots: {type(plots)}, row: {type(plots[0])}, element: {type(plots[0][0])}")

  plt.subplots_adjust(wspace=0.3, hspace=0.3)
  fig.suptitle(f"Station {station_name} Frequency Histograms")
  plots[0, 0].set_title("InterArrival Time")
  plots[0, 1].set_title("InterDeparture Time")
  plots[0, 0].hist(iat)
  plots[0, 1].hist(idt)

  stagePlots("End to End", plots[1, 0], plots[1, 1], sojourn, e2e_wip)
  stagePlots("Inbound Wait", plots[2, 0], plots[2, 1], wait_time, waiting_counts)
  stagePlots("Station Processing", plots[3, 0], plots[3, 1], processing_time, processing_counts)
  stagePlots("Outbound Wait", plots[4, 0], plots[4, 1], departure_time, departure_counts)

  # Sharing Axis

  for timePlot in [plots[2, 0], plots[3, 0], plots[4, 0]]:
    timePlot.sharex(plots[1, 0])
    timePlot.sharey(plots[1, 0])

  for wipPlot in [plots[2, 1], plots[3, 1], plots[4, 1]]:
    wipPlot.sharex(plots[1, 1])
    wipPlot.sharey(plots[1, 1])

  plt.show()


def plotThroughput(station_name: str, n_slots: int, db_engine: Engine) -> None:
  df: pd.DataFrame = pd.read_sql_query(f"select * from event_record where op_type = 'END' and station = '{station_name}' order by batch, at asc", con=db_engine)
  t_max: int = int(df['at'].max()) # type: ignore
  t_min: int = int(df['at'].min()) # type: ignore
  window_length = int(float(t_max - t_min)/float(n_slots))

  def window_index(at: int) -> int:
    return int(float(n_slots*(at - t_min))/float(window_length))

  df.loc[:, 'time period'] = df['at'].apply(window_index)

  th = df.groupby('time period')['job'].count()

  plt.title(f"{station_name} Throughput per period")
  plt.xlabel(f"period = {window_length}")
  plt.ylabel('Jobs per period')
  th.plot.bar(rot=45)
