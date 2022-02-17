package fr.janalyse.zwords.dictionary

import zio.*

trait DictionaryService:
  def count: Task[Int]
  def wordAt(index: Int): Task[String]
  def wordExists(word:String): Task[Boolean]

object DictionaryService:
  def count: ZIO[DictionaryService, Throwable, Int] =
    ZIO.serviceWithZIO(_.count)

  def wordAt(index: Int): ZIO[DictionaryService, Throwable, String] =
    ZIO.serviceWithZIO(_.wordAt(index))

  def wordExists(word:String): ZIO[DictionaryService, Throwable, Boolean] =
    ZIO.serviceWithZIO(_.wordExists(word))

  def live: ULayer[DictionaryService] = ZLayer.succeed(
    DictionaryServiceImpl()
  )

class DictionaryServiceImpl extends DictionaryService:
  private val words = Vector(
    "Armageddon",
    "Etagere",
    "Dopage",
    "Dictionnaire",
    "Evaluer",
    "Parafoudre",
    "terminer",
    "Reptile",
  ).map(_.toUpperCase).distinct

  override def count: Task[Int] = Task(words.size)

  override def wordAt(index: Int): Task[String] =
    if index < 0 || index >= words.size
    then Task.fail(Exception(s"Invalid dictionary index value $index"))
    else Task(words(index))

  override def wordExists(word: String): Task[Boolean] =
    Task(words.contains(word))
