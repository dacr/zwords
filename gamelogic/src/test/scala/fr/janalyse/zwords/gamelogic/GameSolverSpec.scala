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
package fr.janalyse.zwords.gamelogic

import fr.janalyse.zwords.dictionary.DictionaryService
import fr.janalyse.zwords.wordgen.WordGeneratorService
import zio.*
import zio.json.*
import zio.test.*
import zio.test.Assertion.*
import zio.test.TestAspect.*

object GameSolverSpec extends DefaultRunnableSpec {

  override def spec = {
    suite("Game solver spec")(
      test("Solver scenario 1") {
        val chosen     = "FOLIE"
        val playedRows = List("FANER", "FETES", "FOINS").map(played => GuessRow.buildRow(chosen, played))
        val pattern    = GuessRow.buildPatternRowPlayedRows(playedRows).pattern
        val knownPlaces = GameSolver.knownPlaces(playedRows).keys.toSet
        val included = GameSolver.possiblePlaces(playedRows)
        val excluded = GameSolver.impossiblePlaces(playedRows)
        assertTrue(
          pattern == "FO___",
          knownPlaces == Set(0,1),
          included.size == 2,
          included('E') == Set(2,4),
          included('I') == Set(3,4),
          excluded.size == 3,
          excluded(2) == "INTARS".toSet,
          excluded(3) == "ENTARS".toSet,
          excluded(4) == "NTARS".toSet
        )
      },
      test("Solver scenario 2") {
        val chosen     = "RIGOLOTE"
        val playedRows = List("RESTAURE", "RONFLEUR", "RIPOSTES").map(played => GuessRow.buildRow(chosen, played))
        val pattern    = GuessRow.buildPatternRowPlayedRows(playedRows).pattern
        val knownPlaces = GameSolver.knownPlaces(playedRows).keys.toSet
        val included = GameSolver.possiblePlaces(playedRows)
        val excluded = GameSolver.impossiblePlaces(playedRows)
        assertTrue(
          pattern == "RI_OL__E",
          knownPlaces == Set(0,1,3,4,7),
          included.size == 1,
          included('T') == Set(2,6),
          excluded.size == 3,
          excluded(2) == "ENUFAPRS".toSet,
          excluded(5) == "ETNUFAPRS".toSet,
          excluded(6) == "ENUFAPRS".toSet
        )
      }
    )
  }
}
