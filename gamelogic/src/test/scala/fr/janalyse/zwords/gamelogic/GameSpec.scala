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

object GameSpec extends DefaultRunnableSpec {

  override def spec = {
    suite("game logic spec")(
      test("game example 1") {
        for {
          round0 <- Game.init("FOLIE", 6)
          round1 <- round0.play("FANER")
          round2 <- round1.play("FETES")
          round3 <- round2.play("FOINS")
          _      <- Console.printLine(round3)
          round4 <- round3.play("FOLIE")
        } yield assertTrue(
          round0.possibleWordsCount == 184,
          List(round0, round1, round2, round3).forall(_.isWin == false),
          List(round0, round1, round2, round3).forall(_.isLost == false),
          List(round0, round1, round2, round3).forall(_.isOver == false),
          round4.isWin,
          round4.isOver,
          !round4.isLost
        )
      },
      test("game example 2") {
        for {
          round0 <- Game.init("RIGOLOTE", 6)
          round1 <- round0.play("RESTAURE")
          round2 <- round1.play("RONFLEUR")
          round3 <- round2.play("RIPOSTES")
          _      <- Console.printLine(round3)
          round4 <- round3.play("RIGOLOTE")
        } yield assertTrue(
          round0.possibleWordsCount == 577,
          List(round0, round1, round2, round3).forall(_.isWin == false),
          List(round0, round1, round2, round3).forall(_.isLost == false),
          List(round0, round1, round2, round3).forall(_.isOver == false),
          round4.isWin,
          round4.isOver,
          !round4.isLost
        )
      }
    ) @@ sequential
  }.provideShared(
    DictionaryService.live.mapError(err => TestFailure.fail(Exception(s"Can't initialize dictionary service $err"))),
    WordGeneratorService.live.mapError(err => TestFailure.fail(Exception(s"Can't initialize word generator service $err"))),
    Clock.live,
    Random.live,
    Console.live,
    System.live
  )
}
