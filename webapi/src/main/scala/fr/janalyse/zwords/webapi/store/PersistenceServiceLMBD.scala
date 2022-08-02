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
import fr.janalyse.zwords.webapi.store.model.*
import java.util.UUID

case class PersistenceServiceLMBD(lmdb: LMDBOperations) extends PersistenceService {
  import PersistenceServiceLMBD.*

  override def getPlayer(playerId: UUID): Task[Option[StoredPlayer]] =
    lmdb.fetch(dbNamePlayers, playerId.toString)

  override def upsertPlayer(player: StoredPlayer): Task[StoredPlayer] =
    lmdb.upsertOverwrite[StoredPlayer](dbNamePlayers, player.playerId.toString, player)

  override def deletePlayer(playerId: UUID): Task[Boolean] =
    lmdb.delete(dbNamePlayers, playerId.toString)

  private def makeCurrentGameStoreId(playerId: UUID, languageKey: String): String =
    s"$playerId-$languageKey"

  override def getCurrentGame(playerId: UUID, languageKey: String): Task[Option[StoredCurrentGame]] =
    lmdb.fetch(dbNameCurrentGames, makeCurrentGameStoreId(playerId, languageKey))

  override def upsertCurrentGame(playerId: UUID, languageKey: String, game: StoredCurrentGame): Task[StoredCurrentGame] =
    lmdb.upsertOverwrite[StoredCurrentGame](dbNameCurrentGames, makeCurrentGameStoreId(playerId, languageKey), game)

  override def deleteCurrentGame(playerId: UUID, languageKey: String): Task[Boolean] =
    lmdb.delete(dbNameCurrentGames, makeCurrentGameStoreId(playerId, languageKey))

  override def getGlobalStats(languageKey: String): Task[Option[StoredPlayerStats]] =
    lmdb.fetch(dbNameGlobalStats, languageKey)

  override def upsertGlobalStats(languageKey: String, modifier: Option[StoredPlayerStats] => StoredPlayerStats): Task[StoredPlayerStats] =
    lmdb.upsert(dbNameGlobalStats, languageKey, modifier)

  private def makeDailyStatsStoreId(dailyId: String, languageKey: String): String =
    s"$dailyId-$languageKey"

  override def getDailyStats(dailyId: String, languageKey: String): Task[Option[StoredPlayedStats]] =
    lmdb.fetch(dbNameDailyStats, makeDailyStatsStoreId(dailyId, languageKey))

  override def upsertDailyStats(dailyId: String, languageKey: String, modifier: Option[StoredPlayedStats] => StoredPlayedStats): Task[StoredPlayedStats] =
    lmdb.upsert(dbNameDailyStats, makeDailyStatsStoreId(dailyId, languageKey), modifier)
}

object PersistenceServiceLMBD {
  val dbNamePlayers     = "players"
  val dbNameCurrentGames = "current-games"
  val dbNameDailyStats   = "daily-stats"
  val dbNameGlobalStats  = "global-stats"

  val autoCreateCollections = List(
    dbNamePlayers,
    dbNameCurrentGames,
    dbNameDailyStats,
    dbNameGlobalStats
  )

  def setup(lmdb: LMDBOperations) = {
    ZIO.foreach(autoCreateCollections)(collection => lmdb.databaseCreate(collection)) *> ZIO.succeed(PersistenceServiceLMBD(lmdb))
  }
}
