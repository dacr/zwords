package fr.janalyse.zwords.dictionary

import com.typesafe.config.ConfigFactory
import zio.config.typesafe.TypesafeConfigProvider
import zio.{Runtime, ZLayer}
import zio.test.{TestEnvironment, ZIOSpecDefault, testEnvironment}

abstract class BaseSpecDefault extends ZIOSpecDefault {
  override val bootstrap: ZLayer[Any, Any, TestEnvironment] = {
    val configProviderLayer = {
      val config   = ConfigFactory.load()
      val provider = TypesafeConfigProvider.fromTypesafeConfig(config).kebabCase
      Runtime.setConfigProvider(provider)
    }
    configProviderLayer >>> testEnvironment
  }
}
