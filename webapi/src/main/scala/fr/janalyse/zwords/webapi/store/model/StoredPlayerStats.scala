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

import zio.json.*
import zio.lmdb.json.LMDBCodecJson

case class StoredPlayerStats(
  playedCount: Int = 0, // number of finished game either win or lost
  wonCount: Int = 0,
  lostCount: Int = 0,
  triedCount: Int = 0,  // players count who try at least to play once
  wonIn: Map[String, Int] = Map.empty
) derives LMDBCodecJson
