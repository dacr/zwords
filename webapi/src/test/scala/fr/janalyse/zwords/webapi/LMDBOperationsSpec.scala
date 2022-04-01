package fr.janalyse.zwords.webapi
import fr.janalyse.zwords.webapi.store.LMDBOperations
import zio.*
import zio.test.*
import zio.json.*
import zio.json.ast.Json
import zio.json.ast.Json.*
import zio.test.Gen.*

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
    test("basic operations") {
      check(
        stringBounded(1, 511)(asciiChar), // TODO take care string -> bytes can be more than *2 for unicode... !
        string
      ) { (key, data) =>
        val value = Str(data)
        for {
          lmdb          <- ZIO.service[LMDBOperations]
          _             <- lmdb.upsert[Str](key, value).tapError(err => ZIO.log(s"$key = $data"))
          gotten        <- lmdb.fetch[Str](key).some.tapError(err => ZIO.log(s"$key = $data"))
          _             <- lmdb.upsert(key, Str("updated")).tapError(err => ZIO.log(s"$key = $data"))
          gottenUpdated <- lmdb.fetch[Str](key).some.tapError(err => ZIO.log(s"$key = $data"))
          _             <- lmdb.delete(key).tapError(err => ZIO.log(s"$key = $data"))
          isFailed      <- lmdb.fetch[Str](key).tapError(err => ZIO.log(s"$key = $data")).isFailure
        } yield assertTrue(
          gotten == value,
          gottenUpdated.value == "updated"
        )
      }
    }
  ).provideSomeLayer(lmdbLayer.mapError(err => TestFailure.fail(err)))
}
