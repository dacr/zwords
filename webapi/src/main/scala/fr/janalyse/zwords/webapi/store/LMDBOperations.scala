package fr.janalyse.zwords.webapi.store

import zio.*
import zio.json.*
import java.io.File
import org.lmdbjava.{Dbi, Env}
import org.lmdbjava.DbiFlags
import java.nio.charset.StandardCharsets

import java.nio.ByteBuffer
import java.time.OffsetDateTime

/*
REQUIRED OPTIONS WHEN USED WITH RECENT JVM
--add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/sun.nio.ch=ALL-UNNAMED
*/

case class LMDBOperations(databasePath: File) {
  val dbName  = "zwords-db"
  val charset = StandardCharsets.UTF_8

  private val env: Env[ByteBuffer] = try {
    Env
      .create()
      .setMapSize(500_000_000)
      .setMaxDbs(1)
      .open(databasePath)
  } catch {
    case ex:Throwable =>
      ex.printStackTrace()
      throw new RuntimeException(ex)
  }

  private val db: Dbi[ByteBuffer] = env.openDbi(dbName, DbiFlags.MDB_CREATE)

  def fetch[T](id: String)(using JsonDecoder[T]): Task[Option[T]] = {
    ZIO.acquireReleaseWith(
      ZIO.attemptBlocking(env.txnRead()),
      txn => URIO(txn.close()),
      txn => {
        for {
          key      <- ZIO.attemptBlocking(ByteBuffer.allocateDirect(env.getMaxKeySize))
          _        <- ZIO.attemptBlocking(key.put(id.getBytes(charset)).flip)
          found    <- ZIO.attemptBlocking(Option(db.get(txn, key)).isDefined)
          document <- if (!found) ZIO.succeed(Option.empty)
                      else
                        for {
                          fetchedValue    <- ZIO.attemptBlocking(txn.`val`())
                          decodedDocument <- ZIO.fromEither(charset.decode(fetchedValue).fromJson[T]).mapError(msg => Exception(msg))
                        } yield Some(decodedDocument)
        } yield document
      }
    )
  }

  def upsert[T](document: T, idExtractor: T => String)(using JsonEncoder[T]): Task[Unit] = {
    val id      = idExtractor(document)
    val jsondoc = document.toJson
    for {
      key   <- ZIO.attemptBlocking(ByteBuffer.allocateDirect(env.getMaxKeySize))
      value <- ZIO.attemptBlocking(ByteBuffer.allocateDirect(jsondoc.length * 2 + 10))
      _     <- ZIO.attemptBlocking(key.put(id.getBytes(charset)).flip)
      _     <- ZIO.attemptBlocking(value.put(jsondoc.getBytes(charset)).flip)
      _     <- ZIO.attemptBlocking(db.put(key, value))
    } yield ()
  }

}
