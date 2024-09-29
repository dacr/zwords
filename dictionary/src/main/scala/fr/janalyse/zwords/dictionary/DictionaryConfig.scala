package fr.janalyse.zwords.dictionary

import zio.*
import zio.config.*
import zio.config.magnolia.*
import zio.config.typesafe.*
import zio.config.toKebabCase

case class DictionaryConfig(name: String, affFilename: Option[String], dicFilename: Option[String], subsetFilename: Option[String])

case class DictionariesConfig(dictionaries: Map[String, DictionaryConfig])

object DictionaryConfig {
  val config =
    deriveConfig[DictionariesConfig]
      .nested("dictionaries-config")
}
