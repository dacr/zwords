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

import zio.{Ref, Task}

import java.util.UUID

case class PersistenceServiceLMBD(lmdbAPI: LMDBOperations) extends PersistenceService {
  val globalStatsKey = "global-stats"

  def getPlayer(playerUUID: UUID): Task[Option[Player]] =
    lmdbAPI.fetch(playerUUID.toString)

  def upsertPlayer(player: Player): Task[Player] =
    lmdbAPI.upsertOverwrite[Player](player.uuid.toString, player)

  def getGlobalStats: Task[Option[GlobalStats]] =
    lmdbAPI.fetch(globalStatsKey)

  def upsertGlobalStats(modifier: Option[GlobalStats] => GlobalStats): Task[GlobalStats] =
    lmdbAPI.upsert(globalStatsKey, modifier)

  def getDailyStats(dailyId: String): Task[Option[DailyStats]] =
    lmdbAPI.fetch(dailyId)

  def upsertDailyStats(dailyId: String, modifier: Option[DailyStats] => DailyStats): Task[DailyStats] =
    lmdbAPI.upsert(dailyId, modifier)

}
