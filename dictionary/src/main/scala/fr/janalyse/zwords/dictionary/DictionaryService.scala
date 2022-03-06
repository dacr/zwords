package fr.janalyse.zwords.dictionary

import zio.*

trait DictionaryService:
  def count: Task[Int]
  def entries: Task[Chunk[HunspellEntry]]
  def find(word:String): Task[Option[HunspellEntry]]
  def generateWords(entry: HunspellEntry): Task[List[String]]

object DictionaryService:
  def count: ZIO[DictionaryService, Throwable, Int] =
    ZIO.serviceWithZIO(_.count)

  def entries: ZIO[DictionaryService, Throwable, Chunk[HunspellEntry]] =
    ZIO.serviceWithZIO(_.entries)

  def find(word:String): ZIO[DictionaryService, Throwable, Option[HunspellEntry]] =
    ZIO.serviceWithZIO(_.find(word))

  def generateWords(entry: HunspellEntry): ZIO[DictionaryService, Throwable, List[String]] =
    ZIO.serviceWithZIO(_.generateWords(entry))

  def live: ZLayer[Console & System, Any, DictionaryService] = (
    for dictionary <- Hunspell.loadHunspellDictionary
    yield DictionaryServiceImpl(dictionary)
  ).toLayer


case class DictionaryServiceImpl(dictionary: Hunspell) extends DictionaryService:

  override def count = Task(dictionary.entries.size)

  override def entries: Task[Chunk[HunspellEntry]] =
    Task(dictionary.entries)

  override def find(word: String):Task[Option[HunspellEntry]] =
    Task(dictionary.entries.find(_.word == word))

  override def generateWords(entry: HunspellEntry): Task[List[String]] =
    Task(
      dictionary.generateWords(entry)
    )