package fr.janalyse.zwords.dictionary

import zio.*
import zio.nio.*
import zio.nio.charset.*
import zio.nio.file.*
import zio.{Chunk, Console, ZIO}

case class HunspellEntry(word: String, flags: Option[String], attributes: Map[String, String]) {
  val isDiv       = attributes.get("po") == Some("div") // Separator
  val isCommun    = word.head.isLower                   // Nom commun
  val isCompound  = word.contains("-")
  val isPropre    = attributes.get("po") == Some("npr")
  val isFirstName = attributes.get("po") == Some("prn")
}

object HunspellEntry {
  def fromLine(line: String): Option[HunspellEntry] = {
    val parts      = line.trim().split("""\s+""").toList
    val attributes =
      parts
        .drop(1)
        .map(_.split(":", 2))
        .collect { case Array(key, value) => key -> value }
        .toMap
    parts.headOption.getOrElse("").split("/", 2) match {
      case Array(word)        => Some(HunspellEntry(word, None, attributes))
      case Array(word, flags) => Some(HunspellEntry(word, Some(flags), attributes))
      case _                  => None
    }
  }
}

case class Hunspell(entries: Chunk[HunspellEntry])

object Hunspell {
  val loadHunspellDictionary = {
    val charset = Charset.Standard.utf8
    for {
      dicFilename <- System.env("ZWORDS_DIC_FILEPATH").some
      dicFile      = Path(dicFilename)
      dicBytes    <- Files.readAllBytes(dicFile)
      content     <- charset.decodeString(dicBytes)
      lines        = content.split("\n").toList
      count       <- ZIO.fromOption(lines.headOption.map(_.toInt))
      _           <- Console.printLine(s"Expecting to find $count hunspell entries")
      specs        = lines.tail
      entries      = specs.flatMap(HunspellEntry.fromLine)
      _           <- Console.printLine(s"Found ${entries.size} hunspell entries")
      // hunspell <- ZIO.cond(entries.size == count, Hunspell(entries), Error("Didn't find the right number of words in dictionary"))
      hunspell     = Hunspell(Chunk.fromIterable(entries)) // No check as count input data looks invalid :(
    } yield hunspell
  }
}