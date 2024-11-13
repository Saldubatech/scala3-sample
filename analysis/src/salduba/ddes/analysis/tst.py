import sqlalchemy
from sqlalchemy.engine.base import Engine


class Tst:
  pass


tst: Tst = Tst()


class Tst2():
  pass


engine: Engine = sqlalchemy.create_engine("asdf")  # pyright: ignore
