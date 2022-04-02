package fr.janalyse.zwords.webapi
import fr.janalyse.zwords.webapi.store.LMDBOperations
import zio.*
import zio.test.*
import zio.json.*
import zio.json.ast.Json
import zio.json.ast.Json.*
import zio.test.Gen.*
import zio.test.TestAspect.{ignore, sequential}

import java.util.UUID

object LMDBOperationsSpec extends DefaultRunnableSpec {

  val lmdbLayer =
    nio.file.Files
      .createTempDirectoryManaged(
        prefix = Some("lmdb"),
        fileAttributes = Nil
      )
      .use(somewhere => LMDBOperations.setup(somewhere.toFile, "testdb"))
      .toLayer

  val keygen = stringBounded(1, 511)(asciiChar)

  override def spec = suite("Lightening Memory Mapped Database abstraction layer spec")(
    // -----------------------------------------------------------------------------
    test("try to set/get a key")(
      check(keygen, string) { (id, data) =>
        val value = Str(data)
        for {
          lmdb   <- ZIO.service[LMDBOperations]
          _      <- lmdb.upsert[Str](id, value)
          gotten <- lmdb.fetch[Str](id).some
        } yield assertTrue(
          gotten == value
        ).map(_.label(s"for key $id"))
      }
    ),
    // -----------------------------------------------------------------------------
    test("try to get an non existent key")(
      for {
        lmdb     <- ZIO.service[LMDBOperations]
        id        = UUID.randomUUID().toString
        isFailed <- lmdb.fetch[Str](id).some.isFailure
      } yield assertTrue(isFailed).map(_.label(s"for key $id"))
    ),
    // -----------------------------------------------------------------------------
    test("basic CRUD operations") {
      check(keygen, string) { (id, data) =>
        val value = Str(data)
        for {
          lmdb          <- ZIO.service[LMDBOperations]
          _             <- lmdb.upsert[Str](id, value)
          gotten        <- lmdb.fetch[Str](id).some
          _             <- lmdb.upsert(id, Str("updated"))
          gottenUpdated <- lmdb.fetch[Str](id).some
          _             <- lmdb.delete(id)
          isFailed      <- lmdb.fetch[Str](id).some.isFailure
        } yield assertTrue(
          gotten == value,
          gottenUpdated.value == "updated",
          isFailed
        ).map(_.label(s"for key $id"))
      }
    },
    // -----------------------------------------------------------------------------
    test("many updates") {
      for {
        lmdb    <- ZIO.service[LMDBOperations]
        id       = UUID.randomUUID().toString
        maxValue = 100_000
        _       <- ZIO.foreach(1.to(maxValue))(i => lmdb.upsert[Num](id, Num(i)))
        num     <- lmdb.fetch[Num](id).some
      } yield assertTrue(
        num.value.intValue() == maxValue
      )
    },
    // -----------------------------------------------------------------------------
    test("safe update in place") {
      for {
        lmdb <- ZIO.service[LMDBOperations]
        id    = UUID.randomUUID().toString
        count = 100_000
        _    <- lmdb.upsert(id, Num(0))
        _    <- ZIO.foreachPar(1.to(count))(i => lmdb.update[Num](id, num => Num(num.value.intValue() + 1)))
        num  <- lmdb.fetch[Num](id).some
      } yield assertTrue(
        num.value.intValue() == count
      )
    }
  ).provideSomeLayer(lmdbLayer.mapError(err => TestFailure.fail(err)))
}
