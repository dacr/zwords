package fr.janalyse.zwords.console

import fr.janalyse.zwords.gamelogic.{Game, GameInternalIssue, GameIssue, GoodPlaceCell}
import fr.janalyse.zwords.dictionary.DictionaryService
import fr.janalyse.zwords.wordgen.WordGeneratorService
import zio.*

import scala.Console.{RED, RESET, YELLOW}
import java.io.IOException

object Main extends ZIOAppDefault {

  def playLogic(game: Game): ZIO[Console & WordGeneratorService, GameIssue | GameInternalIssue | IOException, Game] =
    for
      pattern   <- Task.succeed(game.board.patternRow.pattern)
      _         <- Console.printLine(s"$game")
      word      <- Console.readLine
      nextGame  <- game.play(word)
    yield nextGame

  def consoleBasedRound(game: Game): ZIO[Console & WordGeneratorService, Object, Game] =
    for
      _        <- Console.printLine("--------------------")
      nextGame <- playLogic(game)
                    .tapError(err => Console.printLine(s"$RED$err$RESET"))
                    .retryN(10)
      lastGame <- ZIO.when(!nextGame.board.isOver)(consoleBasedRound(nextGame))
    yield lastGame.getOrElse(nextGame)

  val consoleBasedGame =
    for
      wordgen    <- ZIO.service[WordGeneratorService]
      randomWord <- wordgen.todayWord
      _          <- ZIO.log(s"(today's word is $randomWord)")
      game       <- Game.init(randomWord, 6)
      result     <- consoleBasedRound(game)
      _          <- Console.printLine(result.board)
      _          <- Console.printLine(if result.board.isWin then "YOU WIN" else "YOU LOOSE")
    yield ()

  override def run = consoleBasedGame.provide(
    DictionaryService.live,
    WordGeneratorService.live,
    Clock.live,
    Random.live,
    Console.live,
    System.live
  )

}
