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

import scala.util.matching.Regex

case class HunspellEntry(word: String, flags: Option[String], attributes: Map[String, String]) {
  val isDiv        = attributes.get("po") == Some("div") // Separator
  val isCommonWord = word.head.isLower                   // Nom commun
  val isCompound   = word.contains('-') || word.contains('\'')
  val isProperNoun = word.head.isUpper
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

case class SfxRuleReplace(replace: Option[String], change: Option[String], flags: Option[String], condition: Regex)

case class SfxRule(key: String, alternatives: List[SfxRuleReplace])
object SfxRule:
  private def makePattern(regexPattern: String): Regex = (".*" + regexPattern + "$").r
  def fromString(spec: String): SfxRule                =
    val lines        = spec.split("\n").toList
    val header       = lines.head
    val ruleId       = header.trim.split("""\s+""").drop(1).head
    val replacements =
      lines.tail.map(_.split("""\s+""").toList).collect {
        case "SFX" :: `ruleId` :: "0" :: s"0/$flags" :: regex :: attrs           => SfxRuleReplace(None, None, Some(flags), makePattern(regex))
        case "SFX" :: `ruleId` :: "0" :: s"$change/$flags" :: regex :: attrs     => SfxRuleReplace(None, Some(change), Some(flags), makePattern(regex))
        case "SFX" :: `ruleId` :: replace :: s"$change/$flags" :: regex :: attrs => SfxRuleReplace(Some(replace), Some(change), Some(flags), makePattern(regex))
        case "SFX" :: `ruleId` :: "0" :: "0" :: regex :: attrs                   => SfxRuleReplace(None, None, None, makePattern(regex))
        case "SFX" :: `ruleId` :: "0" :: s"$change" :: regex :: attrs            => SfxRuleReplace(None, Some(change), None, makePattern(regex))
        case "SFX" :: `ruleId` :: replace :: s"$change" :: regex :: attrs        => SfxRuleReplace(Some(replace), Some(change), None, makePattern(regex))
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

  val suffixKeySize = suffixes.keys.map(_.size).head // TODO of course to be refactored !!!

  def decompose(entry: HunspellEntry): List[HunspellEntry] =
    // println(entry.word + " " + entry.flags)
    val result =
      entry.flags
        .map { flags =>
          val rulesToApply = flags.grouped(suffixKeySize).toList
          rulesToApply.flatMap { ruleId =>
            suffixes.get(ruleId.take(suffixKeySize)).map { rule =>
              val generated = rule.alternatives.filter(_.condition.matches(entry.word)).map { replacement =>
                val regex         = replacement.replace.map(_ + "$").getOrElse("$")
                val generatedWord = entry.word.replaceAll(regex, replacement.change.getOrElse(""))
                entry.copy(word = generatedWord) // TODO concatenate additional flags and properties coming from the rule
              }
              // println(s" $ruleId ->" + generated.map(_.word).mkString(","))
              generated
            }
          }
        }
    result.map(expanded => entry :: expanded.flatten).getOrElse(entry :: Nil).distinct

case class Hunspell(entries: Chunk[HunspellEntry], affixRules: AffixRules) {
  def generateWords(entry: HunspellEntry): List[HunspellEntry] = affixRules.decompose(entry)
}

object Hunspell {
  def loadHunspellDictionary(dictionaryConfig: DictionaryConfig): ZIO[Any, DictionaryFatalIssue, Hunspell] =
    ZIO.logSpan("Hunspell dictionary") {
      for {
        _           <- ZIO.log("loading")
        charset      = Charset.Standard.utf8
        // ---------------------------------------------
        affFilename <- ZIO.from(dictionaryConfig.affFilename).orElseFail(DictionaryFatalIssue("Aff filename not provided"))
        affFile      = Path(affFilename)
        affBytes    <- Files.readAllBytes(affFile).orElseFail(DictionaryFatalIssue(s"Couldn't read aff file content $affFile"))
        affContent  <- charset.decodeString(affBytes)
        affixRules   = AffixRules(affContent)
        // ---------------------------------------------
        dicFilename <- ZIO.from(dictionaryConfig.dicFilename).orElseFail(DictionaryFatalIssue("Dic filename not provided"))
        dicFile      = Path(dicFilename)
        dicBytes    <- Files.readAllBytes(dicFile).orElseFail(DictionaryFatalIssue(s"Couldn't read dic file content $affFile"))
        dicContent  <- charset.decodeString(dicBytes)
        dicLines     = dicContent.split("\n").toList
        // ---------------------------------------------
        count       <- ZIO.attempt(dicLines.headOption.map(_.toInt).getOrElse(0)).orElseFail(DictionaryFatalIssue("Couldn't extract words count"))
        _           <- ZIO.log(s"Expecting to find $count hunspell entries")
        specs        = dicLines.tail
        entries      = specs.flatMap(HunspellEntry.fromLine)
        _           <- ZIO.log(s"Found ${entries.size} hunspell entries")
        all          = entries.flatMap(entry => affixRules.decompose(entry))
        _           <- ZIO.log(s"All hunspell generated words ${all.size} (${all.map(_.word).distinct.size} distinct)")
        // hunspell    <- ZIO.cond(entries.size == count, Hunspell(Chunk.fromIterable(entries), affixRules), DicFatalIssue("Didn't find the right number of words in dictionary"))
        hunspell     = Hunspell(Chunk.fromIterable(entries), affixRules) // No check as count input data looks invalid :(
      } yield hunspell
    }
}
