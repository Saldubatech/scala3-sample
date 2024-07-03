from typing import Optional
import os
import sqlalchemy
from sqlalchemy.engine import Engine


def openDb() -> Engine:
  db_name = os.getenv("DB_NAME")
  db_user = os.getenv("DB_USER")
  db_pwd = os.getenv("DB_PASSWORD")
  db_host = os.getenv("DB_HOST")
  _db_port = os.getenv("DB_PORT")
  db_port = int(_db_port) if _db_port is not None else None
  url_template = f"postgresql://{db_user}:{{}}@{db_host}:{db_port}/{db_name}"
  db_url = url_template.format(db_pwd) if (
    db_user is not None and
    db_pwd is not None and
    db_host is not None and
    db_port is not None and
    db_name is not None) else None
  db_engine: Optional[Engine] = sqlalchemy.create_engine(db_url) if db_url is not None else None  # type: ignore

  if db_engine is None:
    raise Exception(f"DB engine not available at {url_template}")
  else:
    return db_engine


def clearRecords() -> None:
  e = openDb()
  with e.connect() as conn:
    conn.execute(sqlalchemy.text("delete from event_record;"))  # type: ignore
    conn.execute(sqlalchemy.text("delete from operation_record;"))  # type: ignore
    conn.commit()  # type: ignore
  return


def clearDb() -> None:
  with openDb().connect() as conn:
    conn.execute(sqlalchemy.text("drop table if exists event_record;"))  # type: ignore
    conn.execute(sqlalchemy.text("drop table if exists operation_record;"))  # type: ignore
    conn.execute(sqlalchemy.text("drop table if exists flyway_migration_record;"))  # type: ignore
    conn.commit()  # type: ignore
  return


if __name__ == "main":
  clearRecords()
