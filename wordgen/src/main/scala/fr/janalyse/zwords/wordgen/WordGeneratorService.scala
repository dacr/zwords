package fr.janalyse.zwords.wordgen

import fr.janalyse.zwords.dictionary.{DictionaryService, HunspellEntry}
import zio.*

trait WordGeneratorService:
  def todayWord: Task[String]
  def wordExists(word: String): Task[Boolean]
  def wordNormalize(word: String): Task[String]
  def matchingWords(pattern: String, includedLetters: Map[Char, Set[Int]], excludedLetters: Map[Int, Set[Char]]): Task[List[String]]

object WordGeneratorService:
  def todayWord: ZIO[WordGeneratorService, Throwable, String] =
    ZIO.serviceWithZIO(_.todayWord)

  def wordExists(word: String): ZIO[WordGeneratorService, Throwable, Boolean] =
    ZIO.serviceWithZIO(_.wordExists(word))

  def wordNormalize(word: String): ZIO[WordGeneratorService, Throwable, String] =
    ZIO.serviceWithZIO(_.wordNormalize(word))

  def matchingWords(pattern: String, includedLetters: Map[Char, Set[Int]], excludedLetters: Map[Int, Set[Char]]): ZIO[WordGeneratorService, Throwable, List[String]] =
    ZIO.serviceWithZIO(_.matchingWords(pattern, includedLetters, excludedLetters))

  def live = (
    for
      clock      <- ZIO.service[Clock]
      random     <- ZIO.service[Random]
      dictionary <- ZIO.service[DictionaryService]
      entries    <- dictionary.entries(true)
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
      .filter(_.size >= 5)

  val selectedWordsSet = selectedWords.toSet

  override def todayWord: Task[String] =
    for
      dateTime <- clock.currentDateTime
      seed      = dateTime.toEpochSecond / 3600 / 24
      count     = selectedWords.size
      _        <- random.setSeed(seed)
      index    <- random.nextIntBetween(0, count)
      word      = selectedWords(index)
      _        <- ZIO.log(s"Choosing word $word at index $index/$count (seed = $seed)")
    yield word

  override def wordExists(word: String): Task[Boolean] =
    Task.succeed(selectedWordsSet.contains(normalize(word)))

  override def wordNormalize(word: String): Task[String] =
    Task.attempt(normalize(word))

  override def matchingWords(pattern: String, includedLettersMap: Map[Char, Set[Int]], excludedLettersMap: Map[Int, Set[Char]]): Task[List[String]] =
    val includedLetters = includedLettersMap.keys.mkString           // TODO temporary
    val excludedLetters = excludedLettersMap.values.flatten.mkString // TODO temporary
    val replacement     =
      normalize(excludedLetters.filterNot(includedLetters.contains)) match
        case "" => "."
        case ex => ex.mkString("[^", "", "]")

    val wordRE = pattern.replaceAll("_", replacement).r

    def mask(givenWord: String): String =
      val word = givenWord
        .zip(pattern)
        .collect { case (l, p) if "_.".contains(p) => l.toString }
        .mkString
      word

    Task(
      selectedWords
        .filter(_.size == pattern.size)
        .filter(wordRE.matches)
        .filter(word => includedLetters.forall(mask(word).contains))
        .toList
    )
