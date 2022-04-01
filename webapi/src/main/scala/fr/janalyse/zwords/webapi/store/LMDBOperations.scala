package fr.janalyse.zwords.webapi.store

import zio.*
import zio.json.*

import java.io.File
import org.lmdbjava.{Dbi, DbiFlags, Env, EnvFlags, Txn}

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
      .setMapSize(10_000_000_000)
      .setMaxDbs(1)
      .open(
        databasePath,
        //EnvFlags.MDB_NOLOCK,
        EnvFlags.MDB_NOSYNC, // Dangerous but quite faster !
      )
  } catch {
    case ex:Throwable =>
      ex.printStackTrace()
      throw new RuntimeException(ex)
  }

  java.lang.Runtime.getRuntime().addShutdownHook( new Thread {
    override def run(): Unit = {
      env.sync(true)
      env.close()
    }
  })

  private val db: Dbi[ByteBuffer] = env.openDbi(dbName, DbiFlags.MDB_CREATE)

  def close():Task[Unit] = for {
    _ <- Task.attemptBlocking(env.sync(true))
    _ <- Task.attemptBlocking(env.close())
  } yield ()


  def delete(id:String):Task[Unit] = {
    val keyBytes = id.getBytes(charset)
    for {
      _        <- ZIO.cond(keyBytes.length <= env.getMaxKeySize, (), Exception(s"Key size is over limit ${env.getMaxKeySize}"))
      key      <- ZIO.attemptBlocking(ByteBuffer.allocateDirect(env.getMaxKeySize))
      _        <- ZIO.attemptBlocking(key.put(id.getBytes(charset)).flip)
      keyFound <- ZIO.attemptBlocking(db.delete(key))
      _        <- ZIO.cond(keyFound, (), Exception(s"key $id Not found - delete impossible"))
    } yield ()
  }

  def fetch[T](id: String)(using JsonDecoder[T]): Task[Option[T]] = {
    val keyBytes = id.getBytes(charset)
    ZIO.acquireReleaseWith(
      ZIO.attemptBlocking(env.txnRead()),
      txn => URIO(txn.close()),
      txn => {
        for {
          _        <- ZIO.cond(keyBytes.length <= env.getMaxKeySize, (), Exception(s"Key size is over limit ${env.getMaxKeySize}"))
          key      <- ZIO.attemptBlocking(ByteBuffer.allocateDirect(keyBytes.length))
          _        <- ZIO.attemptBlocking(key.put(id.getBytes(charset)).flip)
          found    <- ZIO.attemptBlocking(Option(db.get(txn, key)).isDefined)
          document <- if (!found) ZIO.succeed(Option.empty)
                      else
                        for {
                          fetchedValue    <- ZIO.attemptBlocking(txn.`val`())
                          _ <- ZIO.cond(fetchedValue != null, (), Exception(s"Key $key not found"))
                          decodedDocument <- ZIO.fromEither(charset.decode(fetchedValue).fromJson[T]).mapError(msg => Exception(msg))
                        } yield Some(decodedDocument)
        } yield document
      }
    )
  }

  def upsert[T](id: String, document: T)(using JsonEncoder[T]): Task[Unit] = {
    val keyBytes = id.getBytes(charset)
    val jsonDoc = document.toJson
    val jsonDocBytes = jsonDoc.getBytes(charset)
    for {
      _     <- ZIO.cond(keyBytes.length <= env.getMaxKeySize, (), Exception(s"Key size is over limit ${env.getMaxKeySize}"))
      key   <- ZIO.attemptBlocking(ByteBuffer.allocateDirect(keyBytes.size))
      _     <- ZIO.attemptBlocking(key.put(keyBytes).flip)
      value <- ZIO.attemptBlocking(ByteBuffer.allocateDirect(jsonDocBytes.size))
      _     <- ZIO.attemptBlocking(value.put(jsonDocBytes).flip)
      _     <- ZIO.attemptBlocking(db.put(key, value))
    } yield ()
  }

}
