package fr.janalyse.zwords.gamelogic

import fr.janalyse.zwords.*
import fr.janalyse.zwords.wordgen.WordGeneratorService
import zio.*
import zio.json.*

import java.util.UUID
import scala.collection.LazyZip2
import scala.io.AnsiColor.*

sealed trait GuessCell
object GuessCell:
  given JsonCodec[GuessCell] = DeriveJsonCodec.gen

case class GoodPlaceCell(char: Char) extends GuessCell:
  override def toString: String = s"$GREEN_B$char$RESET"
object GoodPlaceCell:
  given JsonCodec[GoodPlaceCell] = DeriveJsonCodec.gen

case class NotUsedCell(char: Char) extends GuessCell:
  override def toString: String = s"$char"
object NotUsedCell:
  given JsonCodec[NotUsedCell] = DeriveJsonCodec.gen

case class WrongPlaceCell(char: Char) extends GuessCell:
  override def toString: String = s"$RED_B$char$RESET"
object WrongPlaceCell:
  given JsonCodec[WrongPlaceCell] = DeriveJsonCodec.gen

case class EmptyCell() extends GuessCell:
  override def toString: String = "_"
object EmptyCell:
  given JsonCodec[EmptyCell] = DeriveJsonCodec.gen

case class GuessRow(state: List[GuessCell]):
  val pattern = state.map {
    case GoodPlaceCell(ch) => ch
    case _                 => '_'
  }.mkString

  override def toString = state.mkString
object GuessRow:
  given JsonCodec[GuessRow] = DeriveJsonCodec.gen

case class Board(currentRow: GuessRow, rows: List[GuessRow], maxRowsCount: Int):
  def isWin: Boolean = currentRow.state.forall(_.isInstanceOf[GoodPlaceCell])

  def isOver: Boolean = isWin || rows.size >= maxRowsCount

  def previousRow: GuessRow = rows.headOption.getOrElse(currentRow)

  override def toString = (currentRow :: rows).reverse.mkString("\n")

object Board:
  given JsonCodec[Board] = DeriveJsonCodec.gen

  def apply(wordMask: String, maxAttemptsCount: Int): Board =
    val chars      = wordMask.toList
    val cells      = wordMask.map {
      case '_' | '.' => EmptyCell()
      case ch        => GoodPlaceCell(ch)
    }
    val initialRow = GuessRow(cells.toList)
    Board(initialRow, Nil, maxAttemptsCount)

sealed trait GameIssue // TODO migrate to union type instead !
case class GameIsOver()                              extends GameIssue
case class GamePlayInvalidSize(word: String)         extends GameIssue
case class GameWordNotInDictionary(word: String)     extends GameIssue
case class GameDictionaryIssue(throwable: Throwable) extends GameIssue
case class GameInternalIssue(throwable: Throwable)   extends GameIssue

case class Game(private val uuid: UUID, private val hiddenWord: String, board: Board, possibleWordsCount: Int):

  override def toString: String = board.toString + s" $YELLOW($possibleWordsCount)$RESET"

  def computeRow(hiddenWord: String, givenWord: String): GuessRow =
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
    GuessRow(cells)

  def computeCurrentRow(rows: List[GuessRow]): GuessRow =
    def flip[A](rows: List[List[A]], accu: List[List[A]] = Nil): List[List[A]] =
      if rows.head.isEmpty then accu
      else flip(rows.map(_.tail), accu :+ rows.map(_.head))

    val flippedCells = flip(rows.map(_.state))
    val newCells     =
      flippedCells.map(cells => cells.find(_.isInstanceOf[GoodPlaceCell]).getOrElse(EmptyCell()))
    GuessRow(newCells)

  def play(roundWord: String): ZIO[WordGeneratorService, GameIssue, Game] =
    for
      wordGen       <- ZIO.service[WordGeneratorService]
      givenWord     <- wordGen.wordNormalize(roundWord).mapError(th => GameInternalIssue(th))
      wordInDic     <- wordGen.wordExists(givenWord).mapError(th => GameDictionaryIssue(th))
      _             <- ZIO.cond(wordInDic, (), GameWordNotInDictionary(givenWord))
      _             <- ZIO.cond(!board.isOver, (), GameIsOver())
      _             <- ZIO.cond(givenWord.size == hiddenWord.size, (), GamePlayInvalidSize(givenWord))
      newRows        = computeRow(hiddenWord, givenWord) :: board.rows
      newCurrentRow  = computeCurrentRow(newRows)
      newBoard       = board.copy(currentRow = newCurrentRow, rows = newRows)
      included       = GameSolver.possiblePlaces(newBoard)
      excluded       = GameSolver.impossiblePlaces(newBoard)
      possibleWords <- wordGen.matchingWords(newBoard.currentRow.pattern, included, excluded).mapError(th => GameInternalIssue(th))
    yield copy(board = newBoard, possibleWordsCount = possibleWords.size)

object Game:
  def init(hiddenWord: String, maxAttemptsCount: Int): ZIO[WordGeneratorService & Random, GameIssue, Game] =
    val wordMask = hiddenWord.head +: hiddenWord.tail.map(_ => "_")
    init(hiddenWord, wordMask.mkString, maxAttemptsCount)

  def init(hiddenWord: String, wordMask: String, maxAttemptsCount: Int): ZIO[WordGeneratorService & Random, GameIssue, Game] =
    for {
      wordGen       <- ZIO.service[WordGeneratorService]
      uuid          <- Random.nextUUID
      board          = Board(wordMask, maxAttemptsCount)
      possibleWords <- wordGen.matchingWords(wordMask, Map.empty, Map.empty).mapError(th => GameInternalIssue(th))
    } yield Game(uuid, hiddenWord, board, possibleWords.size)
