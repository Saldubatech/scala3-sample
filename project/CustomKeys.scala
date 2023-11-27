import sbt.*
import com.typesafe.config.{ConfigFactory, Config}

object CustomKeys {
  val localConfig = settingKey[Option[Config]]("Holds an Option to a local environment configuration")
}