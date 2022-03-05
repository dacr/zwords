package fr.janalyse.zwords.wordgen

import fr.janalyse.zwords.dictionary.{DictionaryService, HunspellEntry}
import zio.*

trait WordGeneratorService:
  def todayWord: Task[String]
  def wordExists(word: String): Task[Boolean]
  def wordNormalize(word: String): Task[String]

object WordGeneratorService:
  def todayWord: ZIO[WordGeneratorService, Throwable, String] =
    ZIO.serviceWithZIO(_.todayWord)

  def wordExists(word: String): ZIO[WordGeneratorService, Throwable, Boolean] =
    ZIO.serviceWithZIO(_.wordExists(word))

  def wordNormalize(word: String): ZIO[WordGeneratorService, Throwable, String] =
    ZIO.serviceWithZIO(_.wordNormalize(word))

  def live = (
    for
      clock      <- ZIO.service[Clock]
      random     <- ZIO.service[Random]
      dictionary <- ZIO.service[DictionaryService]
      entries    <- dictionary.entries
    yield WordGeneratorServiceImpl(clock, random, entries)
  ).toLayer

class WordGeneratorServiceImpl(clock: Clock, random: Random, entries: Chunk[HunspellEntry]) extends WordGeneratorService:

  def normalize(word: String): String =
    word.trim.toLowerCase
      .replaceAll("[áàäâ]", "a")
      .replaceAll("[éèëê]", "e")
      .replaceAll("[íìïî]", "i")
      .replaceAll("[óòöô]", "o")
      .replaceAll("[úùüû]", "u")
      .replaceAll("[ç]", "c")
      .toUpperCase

  val selectedWords =
    entries
      .filter(_.isCommun)
      .filterNot(_.isCompound)
      .map(_.word)
      .map(normalize)
      .filter(_.size > 5)

  val selectedWordsSet = selectedWords.toSet

  override def todayWord: Task[String] =
    for
      dateTime <- clock.currentDateTime
      seed      = dateTime.toEpochSecond / 3600 / 24
      wordCount = selectedWords.size
      _        <- random.setSeed(seed)
      index    <- random.nextIntBetween(0, wordCount)
      word      = selectedWords(index)
    yield word

  override def wordExists(word: String): Task[Boolean] =
    Task.succeed(selectedWordsSet.contains(normalize(word)))

  override def wordNormalize(word: String): Task[String] =
    Task.attempt(normalize(word))
