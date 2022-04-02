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

class LMDBOperations(
  databaseName: String,
  env: Env[ByteBuffer],
  db:Dbi[ByteBuffer],
  writeSemaphore: Semaphore // For single writer semantic
) {
  val charset = StandardCharsets.UTF_8

  private def makeKeyByteBuffer(id:String) = {
    val keyBytes = id.getBytes(charset)
    for {
      _   <- ZIO.cond(keyBytes.length <= env.getMaxKeySize, (), Exception(s"Key size is over limit ${env.getMaxKeySize}"))
      key <- ZIO.attemptBlocking(ByteBuffer.allocateDirect(env.getMaxKeySize))
      _   <- ZIO.attemptBlocking(key.put(id.getBytes(charset)).flip)
    } yield key
  }

  /**
   * release resource
   * @return
   */
  def close():Task[Unit] = for {
    _ <- Task.attemptBlocking(env.sync(true))
    _ <- Task.attemptBlocking(env.close())
  } yield ()

  /**
   * delete record
   * @param id
   * @return
   */
  def delete(id:String):Task[Unit] = writeSemaphore.withPermit {
    for {
      key      <- makeKeyByteBuffer(id)
      keyFound <- ZIO.attemptBlocking(db.delete(key))
      _        <- ZIO.cond(keyFound, (), Exception(s"key $id Not found - delete impossible"))
    } yield ()
  }

  /**
   * fetch a record
   * @param id
   * @return
   */
  def fetch[T](id: String)(using JsonDecoder[T]): Task[Option[T]] = {
    ZIO.acquireReleaseWith(
      ZIO.attemptBlocking(env.txnRead()),
      txn => ZIO.attempt(txn.close()).tapError(err => ZIO.logError(err.toString)).ignore,
      txn => {
        for {
          key      <- makeKeyByteBuffer(id)
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

  /**
   * overwrite or insert a document
   * @param id
   * @param document
   * @tparam T
   * @return
   */
  def upsert[T](id: String, document: T)(using JsonEncoder[T]): Task[Unit] = writeSemaphore.withPermit {
    val jsonDoc = document.toJson
    val jsonDocBytes = jsonDoc.getBytes(charset)
    for {
      key   <- makeKeyByteBuffer(id)
      value <- ZIO.attemptBlocking(ByteBuffer.allocateDirect(jsonDocBytes.size))
      _     <- ZIO.attemptBlocking(value.put(jsonDocBytes).flip)
      _     <- ZIO.attemptBlocking(db.put(key, value))
    } yield ()
  }

  /**
   * safer document update throw a lambda
   * @param id
   * @param modifier
   * @return
   */
  def update[T](id:String, modifier: T => T)(using JsonEncoder[T], JsonDecoder[T]): Task[Option[T]] = writeSemaphore.withPermit {
    ZIO.acquireReleaseWith(
      ZIO.attemptBlocking(env.txnWrite()),
      txn => ZIO.attempt(txn.close()).tapError(err => ZIO.logError(err.toString)).ignore,
      txn =>
        for {
          key          <- makeKeyByteBuffer(id)
          found        <- ZIO.attemptBlocking(Option(db.get(txn, key)).isDefined)
          _            <- ZIO.cond(found, (), Exception(s"Key $key not found"))
          rawBefore    <- ZIO.attemptBlocking(txn.`val`())
          _            <- ZIO.cond(rawBefore != null, (), Exception(s"Key $key not found"))
          docBefore    <- ZIO.fromEither(charset.decode(rawBefore).fromJson[T]).mapError(msg => Exception("msg"))
          docAfter      = modifier(docBefore)
          jsonDocBytes  = docAfter.toJson.getBytes(charset)
          valueBuffer  <- ZIO.attemptBlocking(ByteBuffer.allocateDirect(jsonDocBytes.size))
          _            <- ZIO.attemptBlocking(valueBuffer.put(jsonDocBytes).flip)
          _            <- ZIO.attemptBlocking(db.put(txn, key, valueBuffer))
          _            <- ZIO.attemptBlocking(txn.commit())
        } yield None
    )
  }

}

object LMDBOperations {
  def setup(databasePath: File, databaseName: String) : Task[LMDBOperations] = {
    for {
      env <- Task.attempt(
        Env
          .create()
          .setMapSize(10_000_000_000)
          .setMaxDbs(1)
          .setMaxReaders(8)
          .open(
            databasePath,
            EnvFlags.MDB_NOLOCK,
            EnvFlags.MDB_NOSYNC, // Dangerous but quite faster !
          )
      )
      db <- Task.attempt(
        env.openDbi(databaseName, DbiFlags.MDB_CREATE)
      )
      writeSemaphore <- Semaphore.make(1) // To achieve single writer semantic for LMDB
    } yield new LMDBOperations(databaseName, env, db, writeSemaphore)
  }
}
