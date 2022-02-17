package fr.janalyse.zwords.wordgen

import fr.janalyse.zwords.dictionary.DictionaryService
import zio.*

trait WordGeneratorService:
  def todayWord: Task[String]

object WordGeneratorService:
  def todayWord: ZIO[WordGeneratorService, Throwable, String] =
    ZIO.serviceWithZIO(_.todayWord)

  def live: URLayer[Clock & Random & DictionaryService, WordGeneratorService] = ZLayer(
    for
      clock      <- ZIO.service[Clock]
      random     <- ZIO.service[Random]
      dictionary <- ZIO.service[DictionaryService]
    yield WordGeneratorServiceImpl(clock, random, dictionary)
  )

class WordGeneratorServiceImpl(clock: Clock, random: Random, dictionary: DictionaryService) extends WordGeneratorService:
  override def todayWord: Task[String] =
    for
      dateTime  <- clock.currentDateTime
      seed       = dateTime.toEpochSecond / 3600 / 24
      wordCount <- dictionary.count
      _         <- random.setSeed(seed)
      index     <- random.nextIntBetween(0, wordCount)
      word      <- dictionary.wordAt(index)
    yield word
