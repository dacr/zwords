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
package fr.janalyse.zwords.webapi
import fr.janalyse.zwords.webapi.store.LMDBOperations
import zio.*
import zio.test.*
import zio.json.*
import zio.json.ast.Json
import zio.json.ast.Json.*
import zio.test.Gen.*
import zio.test.TestAspect.{ignore, sequential}

import java.util.UUID

object LMDBOperationsSpec extends ZIOSpecDefault {

  val lmdbLayer = ZLayer.fromZIO(
    for {
      somewhere <- nio.file.Files
                     .createTempDirectoryScoped(
                       prefix = Some("lmdb"),
                       fileAttributes = Nil
                     )
      lmdb      <- LMDBOperations.setup(somewhere.toFile, "testdb")
    } yield lmdb
  )

  val keygen = stringBounded(1, 511)(asciiChar)

  override def spec = suite("Lightening Memory Mapped Database abstraction layer spec")(
    // -----------------------------------------------------------------------------
    test("try to set/get a key")(
      check(keygen, string) { (id, data) =>
        val value = Str(data)
        for {
          lmdb   <- ZIO.service[LMDBOperations]
          _      <- lmdb.upsertOverwrite[Str](id, value)
          gotten <- lmdb.fetch[Str](id).some
        } yield assertTrue(
          gotten == value
        ).label(s"for key $id")
      }
    ),
    // -----------------------------------------------------------------------------
    test("try to get an non existent key")(
      for {
        lmdb     <- ZIO.service[LMDBOperations]
        id        = UUID.randomUUID().toString
        isFailed <- lmdb.fetch[Str](id).some.isFailure
      } yield assertTrue(isFailed).label(s"for key $id")
    ),
    // -----------------------------------------------------------------------------
    test("basic CRUD operations") {
      check(keygen, string) { (id, data) =>
        val value = Str(data)
        for {
          lmdb          <- ZIO.service[LMDBOperations]
          _             <- lmdb.upsertOverwrite[Str](id, value)
          gotten        <- lmdb.fetch[Str](id).some
          _             <- lmdb.upsertOverwrite(id, Str("updated"))
          gottenUpdated <- lmdb.fetch[Str](id).some
          _             <- lmdb.delete(id)
          isFailed      <- lmdb.fetch[Str](id).some.isFailure
        } yield assertTrue(
          gotten == value,
          gottenUpdated.value == "updated",
          isFailed
        ).label(s"for key $id")
      }
    },
    // -----------------------------------------------------------------------------
    test("many overwrite updates") {
      for {
        lmdb    <- ZIO.service[LMDBOperations]
        id       = UUID.randomUUID().toString
        maxValue = 100_000
        _       <- ZIO.foreach(1.to(maxValue))(i => lmdb.upsertOverwrite[Num](id, Num(i)))
        num     <- lmdb.fetch[Num](id).some
      } yield assertTrue(
        num.value.intValue() == maxValue
      )
    },
    // -----------------------------------------------------------------------------
    test("safe update in place") {
      def modifier(from: Option[Num]): Num = from match {
        case None      => Num(1)
        case Some(num) => Num(num.value.intValue() + 1)
      }
      for {
        lmdb <- ZIO.service[LMDBOperations]
        id    = UUID.randomUUID().toString
        count = 100_000
        _    <- ZIO.foreachPar(1.to(count))(i => lmdb.upsert[Num](id, modifier))
        num  <- lmdb.fetch[Num](id).some
      } yield assertTrue(
        num.value.intValue() == count
      )
    }
  ).provideSomeLayer(lmdbLayer.mapError(err => TestFailure.fail(err)))
}
