package fr.janalyse.zwords.gamelogic

import fr.janalyse.zwords.*
import fr.janalyse.zwords.wordgen.WordGeneratorService
import zio.*
import zio.json.*

import java.time.Instant
import java.util.UUID
import scala.collection.LazyZip2
import scala.io.AnsiColor.*

// ==============================================================================

sealed trait GuessCell:
  def char: Char

object GuessCell:
  given JsonCodec[GuessCell] = DeriveJsonCodec.gen

// ---------------------------------------------------------
case class GoodPlaceCell(char: Char) extends GuessCell:
  override def toString: String = s"$GREEN_B$char$RESET"

object GoodPlaceCell:
  given JsonCodec[GoodPlaceCell] = DeriveJsonCodec.gen

// ---------------------------------------------------------
case class NotUsedCell(char: Char) extends GuessCell:
  override def toString: String = s"$char"

object NotUsedCell:
  given JsonCodec[NotUsedCell] = DeriveJsonCodec.gen

// ---------------------------------------------------------
case class WrongPlaceCell(char: Char) extends GuessCell:
  override def toString: String = s"$RED_B$char$RESET"

object WrongPlaceCell:
  given JsonCodec[WrongPlaceCell] = DeriveJsonCodec.gen

// ---------------------------------------------------------
case class EmptyCell() extends GuessCell:
  val char                      = '_'
  override def toString: String = "_"

object EmptyCell:
  given JsonCodec[EmptyCell] = DeriveJsonCodec.gen

// ==============================================================================

case class GuessRow(triedWord: Option[String], state: List[GuessCell]):
  val pattern = state.map {
    case GoodPlaceCell(ch) => ch
    case _                 => '_'
  }.mkString

  override def toString = state.mkString

object GuessRow:
  given JsonCodec[GuessRow] = DeriveJsonCodec.gen

  def buildRow(hiddenWord: String, givenWord: String): GuessRow =
    val hiddenTuples     = hiddenWord.zipWithIndex.map(_.swap).toSet
    val givenTuples      = givenWord.zipWithIndex.map(_.swap).toSet
    val commonTuples     = hiddenTuples.intersect(givenTuples)
    val otherHiddenChars = (hiddenTuples -- commonTuples).toList.collect { case (idx, ch) => ch }
    val otherGivenTuples = (givenTuples -- commonTuples).toList.sorted

    def computeOtherTuples(remainTuples: List[(Int, Char)], remainCharCount: Map[Char, Int], accu: List[(Int, GuessCell)] = Nil): List[(Int, GuessCell)] =
      if remainTuples.isEmpty then accu
      else
        val (pos, char) = remainTuples.head
        val remainCount = remainCharCount.get(char).getOrElse(0)
        if remainCount > 0 then computeOtherTuples(remainTuples.tail, remainCharCount + (char -> (remainCount - 1)), accu :+ (pos -> WrongPlaceCell(char)))
        else computeOtherTuples(remainTuples.tail, remainCharCount, accu :+ (pos              -> NotUsedCell(char)))

    val otherTuples = computeOtherTuples(otherGivenTuples, otherHiddenChars.groupMapReduce(identity)(_ => 1)(_ + _))
    val cells       =
      (commonTuples.map { case (pos, ch) => pos -> GoodPlaceCell(ch) }.toList ++ otherTuples)
        .sortBy { case (pos, cell) => pos }
        .map { case (pos, cell) => cell }
    GuessRow(Some(givenWord), cells)

  def buildPatternRowFromWordMask(wordMask: String): GuessRow =
    val chars = wordMask.toList
    val cells = wordMask.map {
      case '_' | '.' => EmptyCell()
      case ch        => GoodPlaceCell(ch)
    }
    GuessRow(None, cells.toList)

  // Return made only of GoodPlaceCell and EmptyCell
  def buildPatternRowPlayedRows(rows: List[GuessRow]): GuessRow =
    def flip[A](rows: List[List[A]], accu: List[List[A]] = Nil): List[List[A]] =
      if rows.head.isEmpty then accu
      else flip(rows.map(_.tail), accu :+ rows.map(_.head))

    val flippedCells = flip(rows.map(_.state))
    val newCells     =
      flippedCells.map(cells => cells.find(_.isInstanceOf[GoodPlaceCell]).getOrElse(EmptyCell()))
    GuessRow(None, newCells)

// ==============================================================================

case class Board(patternRow: GuessRow, playedRows: List[GuessRow], maxRowsCount: Int):
  def isWin: Boolean = patternRow.state.forall(_.isInstanceOf[GoodPlaceCell])

  def isOver: Boolean = isWin || playedRows.size >= maxRowsCount

  def isLost: Boolean = playedRows.size >= maxRowsCount && !isWin

  override def toString = (patternRow :: playedRows).reverse.mkString("\n")

object Board:
  given JsonCodec[Board] = DeriveJsonCodec.gen

  def apply(wordMask: String, maxAttemptsCount: Int): Board =
    val initialRow = GuessRow.buildPatternRowFromWordMask(wordMask)
    Board(initialRow, Nil, maxAttemptsCount)

// ==============================================================================

sealed trait GameIssue // TODO migrate to union type instead !

case class GameIsOver()                          extends GameIssue
case class GamePlayInvalidSize(word: String)     extends GameIssue
case class GameWordNotInDictionary(word: String) extends GameIssue
case class GameInvalidUUID(uuid: String)         extends GameIssue
case class GameNotFound(uuid: String)            extends GameIssue

sealed trait GameInternalIssue {
  def message: String
}

case class GameDictionaryIssue(message: String)    extends GameInternalIssue
case class GameWordGeneratorIssue(message: String) extends GameInternalIssue
case class GameStorageIssue(message: String)       extends GameInternalIssue

object GameIssue:
  given JsonCodec[GameIssue] = DeriveJsonCodec.gen

object GameIsOver:
  given JsonCodec[GameIsOver] = DeriveJsonCodec.gen

object GamePlayInvalidSize:
  given JsonCodec[GamePlayInvalidSize] = DeriveJsonCodec.gen

object GameWordNotInDictionary:
  given JsonCodec[GameWordNotInDictionary] = DeriveJsonCodec.gen

object GameInternalIssue:
  given JsonCodec[GameInternalIssue] = DeriveJsonCodec.gen

object GameInvalidUUID:
  given JsonCodec[GameInvalidUUID] = DeriveJsonCodec.gen

object GameNotFound:
  given JsonCodec[GameNotFound] = DeriveJsonCodec.gen

object GameDictionaryIssue:
  given JsonCodec[GameDictionaryIssue]          = DeriveJsonCodec.gen
  def apply(th: Throwable): GameDictionaryIssue = GameDictionaryIssue(th.getMessage)

object GameWordGeneratorIssue:
  given JsonCodec[GameWordGeneratorIssue]          = DeriveJsonCodec.gen
  def apply(th: Throwable): GameWordGeneratorIssue = GameWordGeneratorIssue(th.getMessage)

object GameStorageIssue:
  given JsonCodec[GameStorageIssue]          = DeriveJsonCodec.gen
  def apply(th: Throwable): GameStorageIssue = GameStorageIssue(th.getMessage)

case class Game(uuid: UUID, hiddenWord: String, board: Board, createdDate: Instant, possibleWordsCount: Int):

  override def toString: String = board.toString + s" $YELLOW($possibleWordsCount)$RESET"

  def isWin  = board.isWin
  def isOver = board.isOver
  def isLost = board.isLost

  def play(roundWord: String): ZIO[WordGeneratorService, GameIssue | GameInternalIssue, Game] =
    for
      wordGen       <- ZIO.service[WordGeneratorService]
      givenWord     <- wordGen.wordNormalize(roundWord).mapError(th => GameWordGeneratorIssue(th))
      wordInDic     <- wordGen.wordExists(givenWord).mapError(th => GameDictionaryIssue(th))
      _             <- ZIO.cond(wordInDic, (), GameWordNotInDictionary(givenWord))
      _             <- ZIO.cond(!board.isOver, (), GameIsOver())
      _             <- ZIO.cond(givenWord.size == hiddenWord.size, (), GamePlayInvalidSize(givenWord))
      newPlayedRows  = GuessRow.buildRow(hiddenWord, givenWord) :: board.playedRows
      newPatternRow  = GuessRow.buildPatternRowPlayedRows(newPlayedRows)
      newBoard       = board.copy(patternRow = newPatternRow, playedRows = newPlayedRows)
      included       = GameSolver.possiblePlaces(newPlayedRows)
      excluded       = GameSolver.impossiblePlaces(newPlayedRows)
      possibleWords <- wordGen.matchingWords(newBoard.patternRow.pattern, included, excluded).mapError(th => GameWordGeneratorIssue(th))
    yield copy(board = newBoard, possibleWordsCount = possibleWords.size)

object Game:
  def init(maxAttemptsCount: Int): ZIO[WordGeneratorService & Random & Clock, GameIssue | GameInternalIssue, Game] =
    for {
      wordGenerator <- ZIO.service[WordGeneratorService]
      todayWord     <- wordGenerator.todayWord.mapError(th => GameWordGeneratorIssue(th))
      game          <- init(todayWord, maxAttemptsCount)
    } yield game

  def init(hiddenWord: String, maxAttemptsCount: Int): ZIO[WordGeneratorService & Random & Clock, GameIssue | GameInternalIssue, Game] =
    val wordMask = hiddenWord.head +: hiddenWord.tail.map(_ => "_")
    init(hiddenWord, wordMask.mkString, maxAttemptsCount)

  def init(hiddenWord: String, wordMask: String, maxAttemptsCount: Int): ZIO[WordGeneratorService & Random & Clock, GameIssue | GameInternalIssue, Game] =
    for {
      wordGen       <- ZIO.service[WordGeneratorService]
      createdDate   <- Clock.instant
      _             <- Random.setSeed(createdDate.toEpochMilli)
      uuid          <- Random.nextUUID
      board          = Board(wordMask, maxAttemptsCount)
      possibleWords <- wordGen.matchingWords(wordMask, Map.empty, Map.empty).mapError(th => GameWordGeneratorIssue(th))
    } yield Game(uuid, hiddenWord, board, createdDate, possibleWords.size)
