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

  override def getPlayerSession(sessionId: UUID): Task[Option[StoredPlayerSession]] =
    lmdb.fetch(dbNameSessions, sessionId.toString)

  override def upsertPlayerSession(session: StoredPlayerSession): Task[StoredPlayerSession] =
    lmdb.upsertOverwrite[StoredPlayerSession](dbNameSessions, session.sessionId.toString, session)

  override def deletePlayerSession(sessionId: UUID): Task[Boolean] =
    lmdb.delete(dbNameSessions, sessionId.toString)

  private def makeCurrentGameStoreId(sessionId: UUID, languageKey: String): String =
    s"$sessionId-$languageKey"

  override def getCurrentGame(sessionId: UUID, languageKey: String): Task[Option[StoredCurrentGame]] =
    lmdb.fetch(dbNameCurrentGames, makeCurrentGameStoreId(sessionId, languageKey))

  override def upsertCurrentGame(sessionId: UUID, languageKey: String, game: StoredCurrentGame): Task[StoredCurrentGame] =
    lmdb.upsertOverwrite[StoredCurrentGame](dbNameCurrentGames, makeCurrentGameStoreId(sessionId, languageKey), game)

  override def deleteCurrentGame(sessionId: UUID, languageKey: String): Task[Boolean] =
    lmdb.delete(dbNameCurrentGames, makeCurrentGameStoreId(sessionId, languageKey))

  override def getGlobalStats(languageKey: String): Task[Option[GlobalStats]] =
    lmdb.fetch(dbNameGlobalStats, languageKey)

  override def upsertGlobalStats(languageKey: String, modifier: Option[GlobalStats] => GlobalStats): Task[GlobalStats] =
    lmdb.upsert(dbNameGlobalStats, languageKey, modifier)

  private def makeDailyStatsStoreId(dailyId: String, languageKey: String): String =
    s"$dailyId-$languageKey"

  override def getDailyStats(dailyId: String, languageKey: String): Task[Option[DailyStats]] =
    lmdb.fetch(dbNameDailyStats, makeDailyStatsStoreId(dailyId, languageKey))

  override def upsertDailyStats(dailyId: String, languageKey: String, modifier: Option[DailyStats] => DailyStats): Task[DailyStats] =
    lmdb.upsert(dbNameDailyStats, makeDailyStatsStoreId(dailyId, languageKey), modifier)
}

object PersistenceServiceLMBD {
  val dbNameSessions     = "sessions"
  val dbNameCurrentGames = "current-games"
  val dbNameDailyStats   = "daily-stats"
  val dbNameGlobalStats  = "global-stats"

  val autoCreateCollections = List(
    dbNameSessions,
    dbNameCurrentGames,
    dbNameDailyStats,
    dbNameGlobalStats
  )

  def setup(lmdb: LMDBOperations) = {
    ZIO.foreach(autoCreateCollections)(collection => lmdb.databaseCreate(collection)) *> ZIO.succeed(PersistenceServiceLMBD(lmdb))
  }
}
