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

import java.time.LocalDate
import java.util.UUID

case class PersistenceServiceLMBD(lmdb: LMDB) extends PersistenceService {
  import PersistenceServiceLMBD.*

  val playersCollection      = lmdb.collectionGet[String, StoredPlayer](dbNamePlayers)
  val currentGamesCollection = lmdb.collectionGet[String, StoredCurrentGame](dbNameCurrentGames)
  val globalStatsCollection  = lmdb.collectionGet[String, StoredPlayerStats](dbNameGlobalStats)
  val dailyStatsCollection   = lmdb.collectionGet[String, StoredPlayedStats](dbNameDailyStats)
  val dailyResultsCollection = lmdb.collectionGet[String, StoredDailyResult](dbNameDailyResults)

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

  // -------------------------------------------------------------------------------------------------------------------
  // leaderboards : full scans, the collections hold a few thousand records at most

  override def getAllPlayers: Task[List[StoredPlayer]] =
    playersCollection.flatMap(_.collect()).mapError(errorConverter)

  // current games are stored under "<playerId>-<languageKey>", a UUID being 36 characters long
  override def getCurrentGames(languageKey: String): Task[List[(UUID, StoredCurrentGame)]] = {
    val suffix = s"-$languageKey"
    currentGamesCollection
      .flatMap(_.streamWithKeys(keyFilter = key => key.length == 36 + suffix.length && key.endsWith(suffix)).runCollect)
      .mapError(errorConverter)
      .map(_.toList.map((key, game) => UUID.fromString(key.take(36)) -> game))
  }

  // "<day>_<languageKey>_<playerId>" : ISO days sort, so a period is a range of keys
  private def makeDailyResultStoreId(day: LocalDate, languageKey: String, playerId: UUID): String =
    s"${day}_${languageKey}_$playerId"

  override def upsertDailyResult(result: StoredDailyResult): Task[StoredDailyResult] =
    dailyResultsCollection
      .flatMap(_.upsertOverwrite(makeDailyResultStoreId(result.day, result.languageKey, result.playerId), result))
      .mapError(errorConverter)
      .as(result)

  override def getDailyResults(languageKey: String, from: LocalDate, to: LocalDate): Task[List[StoredDailyResult]] = {
    val (first, last) = (from.toString, to.toString)
    def inRange(key: String) = {
      val day = key.take(10)
      day >= first && day <= last && key.drop(11).startsWith(s"${languageKey}_")
    }
    dailyResultsCollection.flatMap(_.collect(keyFilter = inRange)).mapError(errorConverter)
  }
}

object PersistenceServiceLMBD {
  val dbNamePlayers      = "players"
  val dbNameCurrentGames = "current-games"
  val dbNameDailyStats   = "daily-stats"
  val dbNameGlobalStats  = "global-stats"
  val dbNameDailyResults = "daily-results"

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

  // failIfExists = false also registers metadata for collections created by older zio-lmdb releases
  private def createCollections(lmdb: LMDB) =
    lmdb.collectionCreate[String, StoredPlayer](dbNamePlayers, failIfExists = false) *>
      lmdb.collectionCreate[String, StoredCurrentGame](dbNameCurrentGames, failIfExists = false) *>
      lmdb.collectionCreate[String, StoredPlayedStats](dbNameDailyStats, failIfExists = false) *>
      lmdb.collectionCreate[String, StoredPlayerStats](dbNameGlobalStats, failIfExists = false) *>
      lmdb.collectionCreate[String, StoredDailyResult](dbNameDailyResults, failIfExists = false)

  // zio-lmdb 3 changed the shape of its metadata entries and can't decode the ones written by 2.x : the
  // typed collection creation then fails with "Metadata lookup failed". The metadata only holds the catalog
  // (kind and schemas of each collection), not the data : it is cleared once, and createCollections
  // registers every collection again in the new format. See zio-lmdb docs/internal/MIGRATION_METADATA_ENTRY.md
  private def isLegacyMetadata(error: Any): Boolean = error match {
    case StorageSystemError.InternalError(msg, _) => msg.startsWith("Metadata lookup failed")
    case _                                        => false
  }

  def setup(lmdb: LMDB) = {
    createCollections(lmdb)
      .catchSome {
        case error if isLegacyMetadata(error) =>
          ZIO.logWarning(s"Metadata written by an older zio-lmdb, rebuilding it : $error") *>
            lmdb.collectionClear("meta-data") *>
            createCollections(lmdb)
      }
      .mapError(errorConverter)
      .as(PersistenceServiceLMBD(lmdb))
  }
}
