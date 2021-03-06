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

case class SfxRuleReplace(replace: Option[String], change: Option[String], flags: Option[String], attributes: Map[String, String])

case class SfxRule(key: String, alternatives: List[SfxRuleReplace])
object SfxRule:
  def fromString(spec: String): SfxRule =
    val lines        = spec.split("\n").toList
    val header       = lines.head
    val ruleId       = header.trim.split("""\s+""").drop(1).head
    val replacements =
      lines.tail.map(_.split("""\s+""").toList).collect {
        case "SFX" :: `ruleId` :: "0" :: s"0/$flags" :: attrs           => SfxRuleReplace(None, None, Some(flags), Map.empty)
        case "SFX" :: `ruleId` :: "0" :: s"$change/$flags" :: attrs     => SfxRuleReplace(None, Some(change), Some(flags), Map.empty)
        case "SFX" :: `ruleId` :: replace :: s"$change/$flags" :: attrs => SfxRuleReplace(Some(replace), Some(change), Some(flags), Map.empty)
      }
    SfxRule(ruleId, replacements)

case class AffixRules(specs: String):
  val blocs =
    specs
      .split("""\n(\s*\n)+""")
      .map(_.trim)
      .filter(_.size > 0)
      .filterNot(_.startsWith("#"))
      .toList

  val suffixes =
    blocs
      .filter(_.startsWith("SFX"))
      .map(SfxRule.fromString)
      .groupBy(_.key)
      .view
      .mapValues(_.head)
      .toMap

  def decompose(entry: HunspellEntry): List[HunspellEntry] =
    val result =
      entry.flags
        .map { flags =>
          val rulesToApply = flags.grouped(2).toList
          rulesToApply.flatMap { ruleId =>
            suffixes.get(ruleId.take(2)).map { rule =>
              rule.alternatives.map { replacement =>
                val regex         = replacement.replace.map(_ + "$").getOrElse("$")
                val generatedWord = entry.word.replaceAll(regex, replacement.change.getOrElse(""))
                entry.copy(word = generatedWord) // TODO concatenate additional flags and properties coming from the rule
              }
            }
          }
        }
    result.map(expanded => entry :: expanded.flatten).getOrElse(entry :: Nil).distinct

case class Hunspell(entries: Chunk[HunspellEntry], affixRules: AffixRules) {
  def generateWords(entry: HunspellEntry): List[HunspellEntry] = affixRules.decompose(entry)
}

object Hunspell {
  val loadHunspellDictionary: ZIO[Any, DicFatalIssue, Hunspell] = for {
      affFilename <- System.env("ZWORDS_AFF_FILEPATH").some.orElseFail(DicFatalIssue("ZWORDS_AFF_FILEPATH not set"))
      _           <- ZIO.log(s"Using aff file $affFilename")
      charset      = Charset.Standard.utf8
      affFile      = Path(affFilename)
      affBytes    <- Files.readAllBytes(affFile).orElseFail(DicFatalIssue(s"Couldn't read aff file content $affFile"))
      affContent  <- charset.decodeString(affBytes)
      affixRules   = AffixRules(affContent)
      dicFilename <- System.env("ZWORDS_DIC_FILEPATH").some.orElseFail(DicFatalIssue("ZWORDS_DIC_FILEPATH not set"))
      _           <- ZIO.log(s"Using doc file $dicFilename")
      dicFile      = Path(dicFilename)
      dicBytes    <- Files.readAllBytes(dicFile).orElseFail(DicFatalIssue(s"Couldn't read dic file content $affFile"))
      dicContent  <- charset.decodeString(dicBytes)
      dicLines     = dicContent.split("\n").toList
      count       <- ZIO.attempt(dicLines.headOption.map(_.toInt).getOrElse(0)).orElseFail(DicFatalIssue("Couldn't extract words count"))
      _           <- ZIO.log(s"Expecting to find $count hunspell entries")
      specs        = dicLines.tail
      entries      = specs.flatMap(HunspellEntry.fromLine)
      _           <- ZIO.log(s"Found ${entries.size} hunspell entries")
      fullCount    = entries.map(entry => affixRules.decompose(entry).size).sum
      _           <- ZIO.log(s"All hunspell generated words $fullCount")
      // hunspell <- ZIO.cond(entries.size == count, Hunspell(entries), Error("Didn't find the right number of words in dictionary"))
      hunspell     = Hunspell(Chunk.fromIterable(entries), affixRules) // No check as count input data looks invalid :(
    } yield hunspell
}
