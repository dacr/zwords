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
  sessionsRef: Ref[Map[UUID, StoredPlayerSession]],
  gamesRef: Ref[Map[(UUID, String), StoredCurrentGame]],
  dailyStatsRef: Ref[Map[(String, String), DailyStats]],
  globalStatsRef: Ref[Map[String, GlobalStats]]
) extends PersistenceService {

  override def getPlayerSession(sessionId: UUID): Task[Option[StoredPlayerSession]] =
    sessionsRef.get.map(_.get(sessionId))

  override def upsertPlayerSession(session: StoredPlayerSession): Task[StoredPlayerSession] =
    sessionsRef.update(players => players + (session.sessionId -> session)).map(_ => session)

  override def deletePlayerSession(sessionId: UUID): Task[Boolean] =
    sessionsRef.update(_.removed(sessionId)).map(_ => true)

  override def getCurrentGame(sessionId: UUID, languageKey: String): Task[Option[StoredCurrentGame]] =
    gamesRef.get.map(_.get((sessionId, languageKey)))

  override def upsertCurrentGame(sessionId: UUID, languageKey: String, game: StoredCurrentGame): Task[StoredCurrentGame] =
    gamesRef.update(games => games + ((sessionId, languageKey) -> game)).map(_ => game)

  override def deleteCurrentGame(sessionId: UUID, languageKey: String): Task[Boolean] =
    gamesRef.update(_.removed((sessionId, languageKey))).map(_ => true)

  override def getGlobalStats(languageKey: String): Task[Option[GlobalStats]] =
    globalStatsRef.get.map(_.get(languageKey))

  override def upsertGlobalStats(languageKey: String, modifier: Option[GlobalStats] => GlobalStats): Task[GlobalStats] =
    globalStatsRef
      .updateAndGet(entries => entries + (languageKey -> modifier(entries.get(languageKey))))
      .map(entries => entries(languageKey)) // Can not fail as this key has been inserted in the previous operation

  override def getDailyStats(dailyId: String, languageKey: String): Task[Option[DailyStats]] =
    dailyStatsRef.get.map(_.get((dailyId, languageKey)))

  override def upsertDailyStats(dailyId: String, languageKey: String, modifier: Option[DailyStats] => DailyStats): Task[DailyStats] = {
    val key = (dailyId, languageKey)
    dailyStatsRef
      .updateAndGet(entries => entries + (key -> modifier(entries.get(key))))
      .map(entries => entries(key)) // Can not fail as this key has been inserted in the previous operation
  }

}
