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
import zio.json.*
import zio.nio.file.{Files, Path}

import java.time.OffsetDateTime
import java.util.UUID

import fr.janalyse.zwords.gamelogic.Game
import fr.janalyse.zwords.webapi.store.model.*

trait PersistenceService {
  def getPlayerSession(sessionId: UUID): Task[Option[StoredPlayerSession]]
  def upsertPlayerSession(session: StoredPlayerSession): Task[StoredPlayerSession]
  def deletePlayerSession(sessionId: UUID): Task[Boolean]

  def getCurrentGame(sessionId: UUID, languageKey: String): Task[Option[StoredCurrentGame]]
  def upsertCurrentGame(sessionId: UUID, languageKey: String, game: StoredCurrentGame): Task[StoredCurrentGame]
  def deleteCurrentGame(sessionId: UUID, languageKey: String): Task[Boolean]

  def getGlobalStats(languageKey: String): Task[Option[GlobalStats]]
  def upsertGlobalStats(languageKey: String, modifier: Option[GlobalStats] => GlobalStats): Task[GlobalStats]

  def getDailyStats(dailyId: String, languageKey: String): Task[Option[DailyStats]]
  def upsertDailyStats(dailyId: String, languageKey: String, modifier: Option[DailyStats] => DailyStats): Task[DailyStats]
}

object PersistenceService {
  def getPlayerSession(sessionId: UUID): RIO[PersistenceService, Option[StoredPlayerSession]]         = ZIO.serviceWithZIO(_.getPlayerSession(sessionId))
  def upsertPlayerSession(session: StoredPlayerSession): RIO[PersistenceService, StoredPlayerSession] = ZIO.serviceWithZIO(_.upsertPlayerSession(session))
  def deletePlayerSession(sessionId: UUID): RIO[PersistenceService, Boolean]                          = ZIO.serviceWithZIO(_.deletePlayerSession(sessionId))

  def getCurrentGame(sessionId: UUID, languageKey: String): RIO[PersistenceService, Option[StoredCurrentGame]]                     = ZIO.serviceWithZIO(_.getCurrentGame(sessionId, languageKey))
  def upsertCurrentGame(sessionId: UUID, languageKey: String, game: StoredCurrentGame): RIO[PersistenceService, StoredCurrentGame] = ZIO.serviceWithZIO(_.upsertCurrentGame(sessionId, languageKey, game))
  def deleteCurrentGame(sessionId: UUID, languageKey: String): RIO[PersistenceService, Boolean]                                    = ZIO.serviceWithZIO(_.deleteCurrentGame(sessionId, languageKey))

  def getGlobalStats(languageKey: String): RIO[PersistenceService, Option[GlobalStats]]                                          = ZIO.serviceWithZIO(_.getGlobalStats(languageKey))
  def upsertGlobalStats(languageKey: String, modifier: Option[GlobalStats] => GlobalStats): RIO[PersistenceService, GlobalStats] = ZIO.serviceWithZIO(_.upsertGlobalStats(languageKey, modifier))

  def getDailyStats(dailyId: String, languageKey: String): RIO[PersistenceService, Option[DailyStats]]                                        = ZIO.serviceWithZIO(_.getDailyStats(dailyId, languageKey))
  def upsertDailyStats(dailyId: String, languageKey: String, modifier: Option[DailyStats] => DailyStats): RIO[PersistenceService, DailyStats] = ZIO.serviceWithZIO(_.upsertDailyStats(dailyId, languageKey, modifier))

  lazy val mem = ZLayer.fromZIO(
    for {
      sessionsRef    <- Ref.make(Map.empty[UUID, StoredPlayerSession])
      gamesRef       <- Ref.make(Map.empty[(UUID, String), StoredCurrentGame])
      dailyStatsRef  <- Ref.make(Map.empty[(String, String), DailyStats])
      globalStatsRef <- Ref.make(Map.empty[String, GlobalStats])
    } yield PersistenceServiceMemory(sessionsRef, gamesRef, dailyStatsRef, globalStatsRef)
  )

  lazy val live = ZLayer.scoped(
    for {
      directory    <- System.env("ZWORDS_LMDB_PATH").some
      directoryPath = Path(directory)
      _            <- Files.createDirectories(directoryPath)
      lmdb         <- LMDBOperations.setup(directoryPath.toFile)
      persistence  <- PersistenceServiceLMBD.setup(lmdb)
    } yield persistence
  )
}
