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

import fr.janalyse.zwords.gamelogic.Game
import fr.janalyse.zwords.webapi.store.model.StoredPlayedTodayStats
import zio.json.*

import java.time.OffsetDateTime
import java.util.UUID

case class StoredPlayerSession(
  uuid: UUID,
  pseudo: String,
  createdOn: OffsetDateTime,
  lastUpdated: OffsetDateTime,
  stats: StoredPlayedTodayStats,
  game: Game,
  currentWinRank: Option[Int] // rank for the current game if game has been won
)
object StoredPlayerSession:
  given JsonCodec[StoredPlayerSession] = DeriveJsonCodec.gen
