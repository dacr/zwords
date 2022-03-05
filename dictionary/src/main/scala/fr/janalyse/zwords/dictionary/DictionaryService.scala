package fr.janalyse.zwords.dictionary

import zio.*

trait DictionaryService:
  def count: Task[Int]
  def entries: Task[Chunk[HunspellEntry]]

object DictionaryService:
  def count: ZIO[DictionaryService, Throwable, Int] =
    ZIO.serviceWithZIO(_.count)

  def entries: ZIO[DictionaryService, Throwable, Chunk[HunspellEntry]] =
    ZIO.serviceWithZIO(_.entries)

  def live: ZLayer[Console & System, Any, DictionaryService] = (
    for dictionary <- Hunspell.loadHunspellDictionary
    yield DictionaryServiceImpl(dictionary)
  ).toLayer


case class DictionaryServiceImpl(dictionary: Hunspell) extends DictionaryService:

  override def count = Task(dictionary.entries.size)

  override def entries: Task[Chunk[HunspellEntry]] =
    Task(dictionary.entries)
