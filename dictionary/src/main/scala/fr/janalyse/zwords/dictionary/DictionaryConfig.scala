package fr.janalyse.zwords.dictionary

import zio.*
import zio.config.*
import zio.config.typesafe.*
import zio.config.magnolia.*
import zio.config.ConfigDescriptor.*

case class DictionaryConfig(name: String, affFilename: Option[String], dicFilename: Option[String])

case class DictionariesConfig(dictionaries: Map[String, DictionaryConfig])

case class RootConfig(dictionariesConfig: DictionariesConfig)

object DictionaryConfig {
  def layer = ZLayer.fromZIO(
    read(
      descriptor[RootConfig]
        .mapKey(toKebabCase)
        .from(TypesafeConfigSource.fromResourcePath)
    ).map(_.dictionariesConfig)
  )
}
