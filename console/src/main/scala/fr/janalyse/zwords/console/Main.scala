package fr.janalyse.zwords.console

import com.typesafe.config.ConfigFactory
import fr.janalyse.zwords.gamelogic.{Game, GameInternalIssue, GameIssue, GoodPlaceCell}
import fr.janalyse.zwords.dictionary.DictionaryService
import fr.janalyse.zwords.dictionary.DictionaryConfig
import fr.janalyse.zwords.wordgen.{WordGeneratorLanguageNotSupported, WordGeneratorService}
import zio.*
import zio.Runtime.removeDefaultLoggers
import zio.config.typesafe.TypesafeConfigProvider

import scala.Console.{RED, RESET, YELLOW}
import java.io.IOException

object Main extends ZIOAppDefault {
  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] = {
    val configProviderLayer = {
      val config   = ConfigFactory.load()
      val provider = TypesafeConfigProvider.fromTypesafeConfig(config).kebabCase
      Runtime.setConfigProvider(provider)
    }
    configProviderLayer
  }

  def playLogic(game: Game): ZIO[WordGeneratorService, GameIssue | WordGeneratorLanguageNotSupported | IOException, Game] =
    for
      pattern  <- ZIO.succeed(game.board.patternRow.pattern)
      _        <- Console.printLine(s"$game")
      word     <- Console.readLine
      nextGame <- game.play(word)
    yield nextGame

  def consoleBasedRound(game: Game): ZIO[WordGeneratorService, GameIssue | WordGeneratorLanguageNotSupported | IOException, Game] =
    for
      _        <- Console.printLine("--------------------")
      nextGame <- playLogic(game)
                    .tapError(err => Console.printLine(s"$RED$err$RESET"))
                    .retryN(10)
      lastGame <- ZIO.when(!nextGame.board.isOver)(consoleBasedRound(nextGame))
    yield lastGame.getOrElse(nextGame)

  val consoleBasedGame =
    for
      languages  <- WordGeneratorService.languages
      language   <- Console.readLine(s"Language (${languages.mkString("|")}) ?")
      randomWord <- WordGeneratorService.todayWord(language)
      _          <- ZIO.log(s"(today's word is $randomWord)")
      game       <- Game.init(language, randomWord, 6)
      result     <- consoleBasedRound(game)
      _          <- Console.printLine(result.board)
      _          <- Console.printLine(if result.board.isWin then "YOU WIN" else "YOU LOOSE")
    yield ()

  override def run = consoleBasedGame.provide(
    DictionaryService.live,
    WordGeneratorService.live
  )

}
