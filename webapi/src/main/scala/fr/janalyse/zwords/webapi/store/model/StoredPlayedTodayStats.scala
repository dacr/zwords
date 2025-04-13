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
package fr.janalyse.zwords.webapi.store.model

import fr.janalyse.zwords.webapi.store.model.StoredPlayedTodayStats
import zio.json.*
import zio.lmdb.json.LMDBCodecJson

case class StoredPlayedTodayStats(
  playedCount: Int = 0,
  wonCount: Int = 0,
  lostCount: Int = 0,
  triedCount: Int = 0,
  wonIn: Map[String, Int] = Map.empty,
  goodPlaceLetterCount: Int = 0,
  wrongPlaceLetterCount: Int = 0,
  unusedLetterCount: Int = 0
) derives LMDBCodecJson, JsonCodec // TODO enhance zio-lmdb to remove JsonCodec derivation
