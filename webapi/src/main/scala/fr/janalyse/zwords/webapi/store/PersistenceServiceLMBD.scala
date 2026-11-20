/*
 * Copyright 2022 David Crosson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fr.janalyse.zwords.webapi.store
import zio.*
import zio.lmdb.*
import fr.janalyse.zwords.webapi.store.model.*
import zio.lmdb.StorageUserError.CollectionAlreadExists

import java.util.UUID

case class PersistenceServiceLMBD(lmdb: LMDB) extends PersistenceService {
  import PersistenceServiceLMBD.*

  val playersCollection      = lmdb.collectionGet[String, StoredPlayer](dbNamePlayers)
  val currentGamesCollection = lmdb.collectionGet[String, StoredCurrentGame](dbNameCurrentGames)
  val globalStatsCollection  = lmdb.collectionGet[String, StoredPlayerStats](dbNameGlobalStats)
  val dailyStatsCollection   = lmdb.collectionGet[String, StoredPlayedStats](dbNameDailyStats)

  override def getPlayer(playerId: UUID): Task[Option[StoredPlayer]] = {
    val result = for {
      col    <- playersCollection
      player <- col.fetch(playerId.toString)
    } yield player
    result.mapError(errorConverter)
  }

  override def upsertPlayer(player: StoredPlayer): Task[StoredPlayer] = {
    val result = for {
      col   <- playersCollection
      state <- col.upsertOverwrite(player.playerId.toString, player)
    } yield player
    result.mapError(errorConverter)
  }

  override def deletePlayer(playerId: UUID): Task[Boolean] = {
    val result = for {
      col   <- playersCollection
      found <- col.delete(playerId.toString)
    } yield found.isDefined
    result.mapError(errorConverter)
  }

  private def makeCurrentGameStoreId(playerId: UUID, languageKey: String): String =
    s"$playerId-$languageKey"

  override def getCurrentGame(playerId: UUID, languageKey: String): Task[Option[StoredCurrentGame]] = {
    val result = for {
      col    <- currentGamesCollection
      storeId = makeCurrentGameStoreId(playerId, languageKey)
      found  <- col.fetch(storeId)
    } yield found
    result.mapError(errorConverter)
  }

  override def upsertCurrentGame(playerId: UUID, languageKey: String, game: StoredCurrentGame): Task[StoredCurrentGame] = {
    val result = for {
      col    <- currentGamesCollection
      storeId = makeCurrentGameStoreId(playerId, languageKey)
      state  <- col.upsertOverwrite(storeId, game)
    } yield game
    result.mapError(errorConverter)
  }

  override def deleteCurrentGame(playerId: UUID, languageKey: String): Task[Boolean] = {
    val result = for {
      col    <- currentGamesCollection
      storeId = makeCurrentGameStoreId(playerId, languageKey)
      found  <- col.delete(storeId)
    } yield found.isDefined
    result.mapError(errorConverter)
  }

  override def getGlobalStats(languageKey: String): Task[Option[StoredPlayerStats]] = {
    val result = for {
      col   <- globalStatsCollection
      found <- col.fetch(languageKey)
    } yield found
    result.mapError(errorConverter)
  }

  override def upsertGlobalStats(languageKey: String, modifier: Option[StoredPlayerStats] => StoredPlayerStats): Task[StoredPlayerStats] = {
    val result = for {
      col   <- globalStatsCollection
      state <- col.upsert(languageKey, modifier)
    } yield state
    result.mapError(errorConverter)
  }

  private def makeDailyStatsStoreId(dailyId: String, languageKey: String): String =
    s"$dailyId-$languageKey"

  override def getDailyStats(dailyId: String, languageKey: String): Task[Option[StoredPlayedStats]] = {
    val result = for {
      col    <- dailyStatsCollection
      storeId = makeDailyStatsStoreId(dailyId, languageKey)
      found  <- col.fetch(storeId)
    } yield found
    result.mapError(errorConverter)
  }

  override def upsertDailyStats(dailyId: String, languageKey: String, modifier: Option[StoredPlayedStats] => StoredPlayedStats): Task[StoredPlayedStats] = {
    val result = for {
      col    <- dailyStatsCollection
      storeId = makeDailyStatsStoreId(dailyId, languageKey)
      state  <- col.upsert(storeId, modifier)
    } yield state
    result.mapError(errorConverter)
  }
}

object PersistenceServiceLMBD {
  val dbNamePlayers      = "players"
  val dbNameCurrentGames = "current-games"
  val dbNameDailyStats   = "daily-stats"
  val dbNameGlobalStats  = "global-stats"

  val autoCreateCollections = List(
    dbNamePlayers,
    dbNameCurrentGames,
    dbNameDailyStats,
    dbNameGlobalStats
  )

  def errorConverter(error: StorageUserError | StorageSystemError): Exception = {
    import StorageUserError.*
    import StorageSystemError.*
    error match {
      case storageSystemError: StorageSystemError =>
        storageSystemError match {
          case InternalError(msg, Some(cause)) => Exception(s"Internal storage error : $msg", cause)
          case InternalError(msg, None)        => Exception(s"Internal storage error : $msg")
        }
      case storageUserError: StorageUserError     =>
        storageUserError match {
          case CollectionAlreadExists(name)          => Exception(s"Collection $name already exists")
          case CollectionNotFound(name)              => Exception(s"Collection $name not found")
          case CodecFailure(issue)                   => Exception(s"Codec encoding issue : $issue")
          case OverSizedKey(id, expandedSize, limit) => Exception(s"Key size ($expandedSize) is too big, should be below $limit")
          case IndexAlreadyExists(name)              => Exception(s"Index $name already exists")
          case IndexNotFound(name)                   => Exception(s"Index $name not found")
          case NestedWriteTransactionError(txn)      => Exception(s"Nested write transaction not allowed : $txn")
          case KeyAlreadyExists(name, key)           => Exception(s"Key $key already exists in collection $name")
        }
    }
  }

  def setup(lmdb: LMDB) = {
    // failIfExists = false also registers metadata for collections created by older zio-lmdb releases
    (lmdb.collectionCreate[String, StoredPlayer](dbNamePlayers, failIfExists = false) *>
      lmdb.collectionCreate[String, StoredCurrentGame](dbNameCurrentGames, failIfExists = false) *>
      lmdb.collectionCreate[String, StoredPlayedStats](dbNameDailyStats, failIfExists = false) *>
      lmdb.collectionCreate[String, StoredPlayerStats](dbNameGlobalStats, failIfExists = false))
      .mapError(errorConverter)
      .as(PersistenceServiceLMBD(lmdb))
  }
}
