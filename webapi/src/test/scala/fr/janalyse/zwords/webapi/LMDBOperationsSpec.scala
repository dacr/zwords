package fr.janalyse.zwords.webapi
import fr.janalyse.zwords.webapi.store.LMDBOperations
import zio.*
import zio.test.*
import zio.json.*
import zio.json.ast.Json
import zio.json.ast.Json.*
import zio.test.Gen.*
import zio.test.TestAspect.sequential

import java.util.UUID

object LMDBOperationsSpec extends DefaultRunnableSpec {

  val lmdbLayer =
    nio.file.Files
      .createTempDirectoryManaged(
        prefix = Some("lmdb"),
        fileAttributes = Nil
      )
      .use(somewhere => ZIO.attemptBlocking(LMDBOperations(somewhere.toFile)))
      .toLayer

  override def spec = suite("Lightening Memory Mapped Database abstraction layer spec")(
    // -----------------------------------------------------------------------------
    test("try to set/get a key")(
      check(
        stringBounded(1, 511)(asciiChar), // TODO take care string -> bytes can be more than *2 for unicode... !
        string
      ) { (key, data) =>
        val value = Str(data)
        for {
          lmdb   <- ZIO.service[LMDBOperations]
          _      <- lmdb.upsert[Str](key, value)
          gotten <- lmdb.fetch[Str](key).some
        } yield assertTrue(
          gotten == value
        ).map(_.label(s"for key $key"))
      }
    ),
    // -----------------------------------------------------------------------------
    test("try to get an non existent key")(
      for {
        lmdb     <- ZIO.service[LMDBOperations]
        key       = UUID.randomUUID().toString
        isFailed <- lmdb.fetch[Str](key).some.isFailure
      } yield assertTrue(isFailed).map(_.label(s"for key $key"))
    ),
    // -----------------------------------------------------------------------------
    test("basic operations") {
      check(
        stringBounded(1, 511)(asciiChar), // TODO take care string -> bytes can be more than *2 for unicode... !
        string
      ) { (key, data) =>
        val value = Str(data)
        for {
          lmdb          <- ZIO.service[LMDBOperations]
          _             <- lmdb.upsert[Str](key, value)
          gotten        <- lmdb.fetch[Str](key).some
          _             <- lmdb.upsert(key, Str("updated"))
          gottenUpdated <- lmdb.fetch[Str](key).some
          _             <- lmdb.delete(key)
          isFailed      <- lmdb.fetch[Str](key).some.isFailure
        } yield assertTrue(
          gotten == value,
          gottenUpdated.value == "updated",
          isFailed
        ).map(_.label(s"for key $key"))
      }
    }
    // -----------------------------------------------------------------------------
  ).provideSomeLayer(lmdbLayer.mapError(err => TestFailure.fail(err)))
}
