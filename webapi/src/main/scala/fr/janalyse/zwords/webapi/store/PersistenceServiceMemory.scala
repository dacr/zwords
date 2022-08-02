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

case class PersistenceServiceMemory(
  playersRef: Ref[Map[UUID, StoredPlayer]],
  gamesRef: Ref[Map[(UUID, String), StoredCurrentGame]],
  dailyStatsRef: Ref[Map[(String, String), StoredPlayedStats]],
  globalStatsRef: Ref[Map[String, StoredPlayerStats]]
) extends PersistenceService {

  override def getPlayer(playerId: UUID): Task[Option[StoredPlayer]] =
    playersRef.get.map(_.get(playerId))

  override def upsertPlayer(player: StoredPlayer): Task[StoredPlayer] =
    playersRef.update(players => players + (player.playerId -> player)).map(_ => player)

  override def deletePlayer(playerId: UUID): Task[Boolean] =
    playersRef.update(_.removed(playerId)).map(_ => true)

  override def getCurrentGame(playerId: UUID, languageKey: String): Task[Option[StoredCurrentGame]] =
    gamesRef.get.map(_.get((playerId, languageKey)))

  override def upsertCurrentGame(playerId: UUID, languageKey: String, game: StoredCurrentGame): Task[StoredCurrentGame] =
    gamesRef.update(games => games + ((playerId, languageKey) -> game)).map(_ => game)

  override def deleteCurrentGame(playerId: UUID, languageKey: String): Task[Boolean] =
    gamesRef.update(_.removed((playerId, languageKey))).map(_ => true)

  override def getGlobalStats(languageKey: String): Task[Option[StoredPlayerStats]] =
    globalStatsRef.get.map(_.get(languageKey))

  override def upsertGlobalStats(languageKey: String, modifier: Option[StoredPlayerStats] => StoredPlayerStats): Task[StoredPlayerStats] =
    globalStatsRef
      .updateAndGet(entries => entries + (languageKey -> modifier(entries.get(languageKey))))
      .map(entries => entries(languageKey)) // Can not fail as this key has been inserted in the previous operation

  override def getDailyStats(dailyId: String, languageKey: String): Task[Option[StoredPlayedStats]] =
    dailyStatsRef.get.map(_.get((dailyId, languageKey)))

  override def upsertDailyStats(dailyId: String, languageKey: String, modifier: Option[StoredPlayedStats] => StoredPlayedStats): Task[StoredPlayedStats] = {
    val key = (dailyId, languageKey)
    dailyStatsRef
      .updateAndGet(entries => entries + (key -> modifier(entries.get(key))))
      .map(entries => entries(key)) // Can not fail as this key has been inserted in the previous operation
  }

}
