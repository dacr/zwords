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
package fr.janalyse.zwords.dictionary

import zio.*

trait DictionaryService:
  def count: Task[Int]
  def entries(all:Boolean): Task[Chunk[HunspellEntry]]
  def find(word:String): Task[Option[HunspellEntry]]
  def generateWords(entry: HunspellEntry): Task[List[HunspellEntry]]

object DictionaryService:
  def count: ZIO[DictionaryService, Throwable, Int] =
    ZIO.serviceWithZIO(_.count)

  def entries(all:Boolean): ZIO[DictionaryService, Throwable, Chunk[HunspellEntry]] =
    ZIO.serviceWithZIO(_.entries(all))

  def find(word:String): ZIO[DictionaryService, Throwable, Option[HunspellEntry]] =
    ZIO.serviceWithZIO(_.find(word))

  def generateWords(entry: HunspellEntry): ZIO[DictionaryService, Throwable, List[HunspellEntry]] =
    ZIO.serviceWithZIO(_.generateWords(entry))

  val live = ZLayer.fromZIO(
    for dictionary <- Hunspell.loadHunspellDictionary
    yield DictionaryServiceLive(dictionary)
  )


case class DictionaryServiceLive(dictionary: Hunspell) extends DictionaryService:

  override def count = ZIO.succeed(dictionary.entries.size)

  override def entries(all:Boolean): Task[Chunk[HunspellEntry]] =
    if (all) ZIO.succeed(dictionary.entries.flatMap(entry => dictionary.generateWords(entry)))
    else ZIO.succeed(dictionary.entries)

  override def find(word: String):Task[Option[HunspellEntry]] =
    ZIO.succeed(dictionary.entries.find(_.word == word))

  override def generateWords(entry: HunspellEntry): Task[List[HunspellEntry]] =
    ZIO.succeed(
      dictionary.generateWords(entry)
    )