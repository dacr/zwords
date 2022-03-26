package fr.janalyse.zwords.wordgen

import fr.janalyse.zwords.dictionary.{DictionaryService, HunspellEntry}
import zio.*

case class WordStats(
  message: String,
  language: String,
  dictionaryBaseSize: Int,
  dictionaryExpandedSize: Int,
  filteredSelectedWordsCount: Int,
  filteredAcceptableWordsCount: Int
)

trait WordGeneratorService:
  def todayWord: Task[String]
  def wordExists(word: String): Task[Boolean]
  def wordNormalize(word: String): Task[String]
  def matchingWords(pattern: String, includedLetters: Map[Char, Set[Int]], excludedLetters: Map[Int, Set[Char]]): Task[List[String]]
  def stats: Task[WordStats]

object WordGeneratorService:
  def todayWord: ZIO[WordGeneratorService, Throwable, String] =
    ZIO.serviceWithZIO(_.todayWord)

  def wordExists(word: String): ZIO[WordGeneratorService, Throwable, Boolean] =
    ZIO.serviceWithZIO(_.wordExists(word))

  def wordNormalize(word: String): ZIO[WordGeneratorService, Throwable, String] =
    ZIO.serviceWithZIO(_.wordNormalize(word))

  def matchingWords(pattern: String, includedLetters: Map[Char, Set[Int]], excludedLetters: Map[Int, Set[Char]]): ZIO[WordGeneratorService, Throwable, List[String]] =
    ZIO.serviceWithZIO(_.matchingWords(pattern, includedLetters, excludedLetters))

  def stats: ZIO[WordGeneratorService, Throwable, WordStats] =
    ZIO.serviceWithZIO(_.stats)

  val live = (
    for
      clock           <- ZIO.service[Clock]
      random          <- ZIO.service[Random]
      dictionary      <- ZIO.service[DictionaryService]
      selectedEntries <- dictionary.entries(false)
      possibleEntries <- dictionary.entries(true)
    yield WordGeneratorServiceImpl(clock, random, selectedEntries, possibleEntries)
  ).toLayer

class WordGeneratorServiceImpl(clock: Clock, random: Random, selectedEntries: Chunk[HunspellEntry], possibleEntries: Chunk[HunspellEntry]) extends WordGeneratorService:

  def standardize(word: String): String =
    word.trim.toLowerCase
      .replaceAll("[áàäâ]", "a")
      .replaceAll("[éèëê]", "e")
      .replaceAll("[íìïî]", "i")
      .replaceAll("[óòöô]", "o")
      .replaceAll("[úùüû]", "u")
      .replaceAll("[ç]", "c")
      .toUpperCase

  def normalizeEntries(entries: Chunk[HunspellEntry]): IndexedSeq[String] =
    entries
      .filter(_.isCommun)
      .filterNot(_.isCompound)
      .map(_.word)
      .map(standardize)
      .filter(_.size >= 5)
      .filter(_.size <= 10)

  val selectedWords    = normalizeEntries(selectedEntries)
  val possibleWords    = normalizeEntries(possibleEntries)
  val possibleWordsSet = possibleWords.toSet

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
    Task.succeed(possibleWordsSet.contains(standardize(word)))

  override def wordNormalize(word: String): Task[String] =
    Task.attempt(standardize(word))

  override def stats: Task[WordStats] =
    Task.succeed(
      WordStats(
        message = "Used dictionary information",
        language = "français",
        dictionaryBaseSize = selectedEntries.size,
        dictionaryExpandedSize = possibleEntries.size,
        filteredSelectedWordsCount = selectedWords.size,
        filteredAcceptableWordsCount = possibleWords.size
      )
    )

  override def matchingWords(wordMask: String, includedLettersMap: Map[Char, Set[Int]], excludedLettersMap: Map[Int, Set[Char]]): Task[List[String]] =

    val wordRE    = wordMask.replaceAll("_", ".").r
    val excludeRE = 0
      .until(wordMask.size)
      .map { index =>
        excludedLettersMap.get(index).map(_.mkString("[^", "", "]")).getOrElse(".")
      }
      .mkString
      .r

    println("-------------- PATTERN ----------------")
    println(wordMask)
    println(wordRE.toString())
    println("-------------- INCLUDED ----------------")
    println(includedLettersMap.toList.sorted.mkString("\n"))
    println("-------------- EXCLUDED ----------------")
    println(excludedLettersMap.toList.sorted.mkString("\n"))
    println(excludeRE.toString())
    println("----------------------------------------")

    def included(word: String): Boolean =
      includedLettersMap.forall((char, positions) => positions.flatMap(word.lift).contains(char))

    Task(
      selectedWords
        .filter(_.size == wordMask.size)
        .filter(wordRE.matches)
        .filter(excludeRE.matches)
        .filter(included)
        .toList
    )
