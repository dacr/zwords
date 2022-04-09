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

case class PersistenceServiceMemory(
  playersRef: Ref[Map[UUID, Player]],
  dailyStatsRef: Ref[Map[String, DailyStats]],
  globalStatsRef: Ref[Option[GlobalStats]]
) extends PersistenceService {

  def getPlayer(playerUUID: UUID): Task[Option[Player]] =
    for {
      players    <- playersRef.get
      mayBePlayer = players.get(playerUUID)
    } yield mayBePlayer

  def upsertPlayer(player: Player): Task[Player] =
    for {
      _ <- playersRef.getAndUpdate(players => players + (player.uuid -> player))
    } yield player

  def getGlobalStats:Task[Option[GlobalStats]] = ???
  
  def upsertGlobalStats(modifier: Option[GlobalStats]=>GlobalStats):Task[GlobalStats] = ???
  
  def getDailyStats(dailyId:String):Task[Option[DailyStats]] = ???
  
  def upsertDailyStats(dailyId: String, modifier: Option[DailyStats]=> DailyStats): Task[DailyStats] = ???

}
