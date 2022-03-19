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

  val live = (
    for dictionary <- Hunspell.loadHunspellDictionary
    yield DictionaryServiceLive(dictionary)
  ).toLayer


case class DictionaryServiceLive(dictionary: Hunspell) extends DictionaryService:

  override def count = Task(dictionary.entries.size)

  override def entries(all:Boolean): Task[Chunk[HunspellEntry]] =
    if (all) Task(dictionary.entries.flatMap(entry => dictionary.generateWords(entry)))
    else Task(dictionary.entries)

  override def find(word: String):Task[Option[HunspellEntry]] =
    Task(dictionary.entries.find(_.word == word))

  override def generateWords(entry: HunspellEntry): Task[List[HunspellEntry]] =
    Task(
      dictionary.generateWords(entry)
    )