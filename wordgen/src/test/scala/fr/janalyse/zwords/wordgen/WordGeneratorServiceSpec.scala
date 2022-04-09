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
package fr.janalyse.zwords.wordgen

import fr.janalyse.zwords.dictionary.DictionaryService
import zio.*
import zio.test.*
import zio.test.Assertion.*

import java.time.{OffsetDateTime, ZoneOffset}

object WordGeneratorServiceSpec extends DefaultRunnableSpec {

  def makeDate(year: Int, month: Int, day: Int) =
    OffsetDateTime.of(year, month, day, 12, 12, 12, 0, ZoneOffset.UTC)

  override def spec = {
    suite("word generator check")(
      test("words check")(
        for {
          _      <- TestClock.setDateTime(makeDate(2022, 3, 30))
          word1A <- WordGeneratorService.todayWord
          word1B <- WordGeneratorService.todayWord
          word1C <- WordGeneratorService.todayWord
          _      <- TestClock.setDateTime(makeDate(2022, 3, 31))
          word2  <- WordGeneratorService.todayWord
          _      <- TestClock.setDateTime(makeDate(2022, 4, 1))
          word3  <- WordGeneratorService.todayWord
          _      <- TestClock.setDateTime(makeDate(2022, 4, 2))
          word4  <- WordGeneratorService.todayWord
          _      <- TestClock.setDateTime(makeDate(2022, 4, 3))
          word5  <- WordGeneratorService.todayWord
          _      <- TestClock.setDateTime(makeDate(2022, 4, 4))
          word6  <- WordGeneratorService.todayWord
          _      <- TestClock.setDateTime(makeDate(2022, 4, 5))
          word7  <- WordGeneratorService.todayWord
          _      <- TestClock.setDateTime(makeDate(2022, 4, 6))
          word8  <- WordGeneratorService.todayWord
          _      <- TestClock.setDateTime(makeDate(2022, 4, 7))
          word9  <- WordGeneratorService.todayWord
        } yield assertTrue(
          word1A == "COCCOLITE",
          word1B == "COCCOLITE",
          word1C == "COCCOLITE",
          word2 == "RECALCULER",
          word3 == "BUGUER",
          word4 == "SEQUOIA",
          word5 == "GIVRE",
          word6 == "CANON",
          word7 == "ABSURDE",
          word8 == "PLANCHISTE",
          word9 == "DOLEANCE",
        )
      )
    ).provideCustomShared(
      DictionaryService.live.mapError(err => TestFailure.fail(Exception(s"Can't initialize dictionary service $err"))),
      WordGeneratorService.live.mapError(err => TestFailure.fail(Exception(s"Can't initialize word generator service $err")))
    ).provideSomeLayer(
      System.live
    )
  }
}
