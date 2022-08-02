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
  def getPlayer(playerId: UUID): Task[Option[StoredPlayer]]
  def upsertPlayer(player: StoredPlayer): Task[StoredPlayer]
  def deletePlayer(playerId: UUID): Task[Boolean]

  def getCurrentGame(playerId: UUID, languageKey: String): Task[Option[StoredCurrentGame]]
  def upsertCurrentGame(playerId: UUID, languageKey: String, game: StoredCurrentGame): Task[StoredCurrentGame]
  def deleteCurrentGame(playerId: UUID, languageKey: String): Task[Boolean]

  def getGlobalStats(languageKey: String): Task[Option[StoredPlayerStats]]
  def upsertGlobalStats(languageKey: String, modifier: Option[StoredPlayerStats] => StoredPlayerStats): Task[StoredPlayerStats]

  def getDailyStats(dailyId: String, languageKey: String): Task[Option[StoredPlayedStats]]
  def upsertDailyStats(dailyId: String, languageKey: String, modifier: Option[StoredPlayedStats] => StoredPlayedStats): Task[StoredPlayedStats]
}

object PersistenceService {
  def getPlayer(playerId: UUID): RIO[PersistenceService, Option[StoredPlayer]]  = ZIO.serviceWithZIO(_.getPlayer(playerId))
  def upsertPlayer(player: StoredPlayer): RIO[PersistenceService, StoredPlayer] = ZIO.serviceWithZIO(_.upsertPlayer(player))
  def deletePlayer(playerId: UUID): RIO[PersistenceService, Boolean]            = ZIO.serviceWithZIO(_.deletePlayer(playerId))

  def getCurrentGame(playerId: UUID, languageKey: String): RIO[PersistenceService, Option[StoredCurrentGame]]                     = ZIO.serviceWithZIO(_.getCurrentGame(playerId, languageKey))
  def upsertCurrentGame(playerId: UUID, languageKey: String, game: StoredCurrentGame): RIO[PersistenceService, StoredCurrentGame] = ZIO.serviceWithZIO(_.upsertCurrentGame(playerId, languageKey, game))
  def deleteCurrentGame(playerId: UUID, languageKey: String): RIO[PersistenceService, Boolean]                                    = ZIO.serviceWithZIO(_.deleteCurrentGame(playerId, languageKey))

  def getGlobalStats(languageKey: String): RIO[PersistenceService, Option[StoredPlayerStats]]                                                      = ZIO.serviceWithZIO(_.getGlobalStats(languageKey))
  def upsertGlobalStats(languageKey: String, modifier: Option[StoredPlayerStats] => StoredPlayerStats): RIO[PersistenceService, StoredPlayerStats] = ZIO.serviceWithZIO(_.upsertGlobalStats(languageKey, modifier))

  def getDailyStats(dailyId: String, languageKey: String): RIO[PersistenceService, Option[StoredPlayedStats]]                                                      = ZIO.serviceWithZIO(_.getDailyStats(dailyId, languageKey))
  def upsertDailyStats(dailyId: String, languageKey: String, modifier: Option[StoredPlayedStats] => StoredPlayedStats): RIO[PersistenceService, StoredPlayedStats] = ZIO.serviceWithZIO(_.upsertDailyStats(dailyId, languageKey, modifier))

  lazy val mem = ZLayer.fromZIO(
    for {
      playersRef     <- Ref.make(Map.empty[UUID, StoredPlayer])
      gamesRef       <- Ref.make(Map.empty[(UUID, String), StoredCurrentGame])
      dailyStatsRef  <- Ref.make(Map.empty[(String, String), StoredPlayedStats])
      globalStatsRef <- Ref.make(Map.empty[String, StoredPlayerStats])
    } yield PersistenceServiceMemory(playersRef, gamesRef, dailyStatsRef, globalStatsRef)
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
