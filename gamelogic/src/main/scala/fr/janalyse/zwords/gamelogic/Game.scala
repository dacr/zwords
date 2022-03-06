package fr.janalyse.zwords.gamelogic

import fr.janalyse.zwords.*
import fr.janalyse.zwords.wordgen.WordGeneratorService
import zio.*

import java.util.UUID
import scala.collection.LazyZip2
import scala.io.AnsiColor.*

sealed trait GuessCell

case class GoodPlaceCell(char: Char) extends GuessCell:
  override def toString: String = s"$GREEN_B$char$RESET"

case class NotUsedCell(char: Char) extends GuessCell:
  override def toString: String = s"$char"

case class WrongPlaceCell(char: Char) extends GuessCell:
  override def toString: String = s"$RED_B$char$RESET"

case class EmptyCell() extends GuessCell:
  override def toString: String = "_"

case class GuessRow(state: List[GuessCell]):
  val pattern = state.map {
    case GoodPlaceCell(ch) => ch
    case _                 => '_'
  }.mkString

  val knownPlaces: Map[Int, Char] =
    state.zipWithIndex.collect { case (GoodPlaceCell(ch), index) => index -> ch }.toMap

  val knownPlacesIndices: Set[Int] =
    state.zipWithIndex.collect { case (GoodPlaceCell(ch), index) => index }.toSet

  val possiblePlaces: Map[Char, Set[Int]] =
    state.zipWithIndex.collect { case (WrongPlaceCell(ch), index) =>
      ch -> state.zipWithIndex.flatMap {
        case (WrongPlaceCell(ch), currentIndex) if currentIndex != index => Some(currentIndex)
        case (NotUsedCell(ch), currentIndex)                             => Some(currentIndex)
        case (_, _)                                                      => None
      }.toSet
    }.toMap

  val impossiblePlaces: Map[Int, Set[Char]] =
    state
      .collect { case NotUsedCell(ch) => ch }
      .toSet
      .flatMap(ch =>
        state.zipWithIndex.flatMap {
          case (WrongPlaceCell(_), currentIndex) => Some(currentIndex -> ch)
          case (NotUsedCell(_), currentIndex)    => Some(currentIndex -> ch)
          case (_, _)                            => None
        }
      )
      .groupMap((pos, ch) => pos)((pos, ch) => ch)

  override def toString = state.mkString

case class Board(currentRow: GuessRow, rows: List[GuessRow], maxRowsCount: Int):
  def isWin: Boolean = currentRow.state.forall(_.isInstanceOf[GoodPlaceCell])

  def isOver: Boolean = isWin || rows.size >= maxRowsCount

  def previousRow: GuessRow = rows.headOption.getOrElse(currentRow)

  def knownPlaces: Map[Int, Char] = rows.flatMap(_.knownPlaces).toMap

  def knownPlacesIndices: Set[Int] = rows.flatMap(_.knownPlacesIndices).toSet

  def impossiblePlaces: Map[Int, Set[Char]] =
    rows
      .map(_.impossiblePlaces)
      .reduceOption((a, b) =>
        (a.toList ++ b.toList)
          .groupMapReduce((position, _) => position)((_, chars) => chars)((aChars, bChars) => aChars ++ bChars)
      )
      .getOrElse(Map.empty)
      .removedAll(knownPlacesIndices)
      .filterNot( (pos,chars) => chars.isEmpty)

  def possiblePlaces: Map[Char, Set[Int]] =
    rows
      .flatMap(_.possiblePlaces)
      .groupMapReduce((ch, _) => ch)((_, pos) => pos)((a, b) => (a ++ b).removedAll(knownPlacesIndices))
      .filterNot( (ch,pos) => pos.isEmpty)

  override def toString = (currentRow :: rows).reverse.mkString("\n")

object Board:
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

case class Game(private val uuid: UUID, private val hiddenWord: String, board: Board):

  def computeRow(hiddenWord: String, givenWord: String): GuessRow =
    val hiddenTuples                                                                                                                                     = hiddenWord.zipWithIndex.map(_.swap).toSet
    val givenTuples                                                                                                                                      = givenWord.zipWithIndex.map(_.swap).toSet
    val commonTuples                                                                                                                                     = hiddenTuples.intersect(givenTuples)
    val otherHiddenChars                                                                                                                                 = (hiddenTuples -- commonTuples).toList.collect { case (idx, ch) => ch }
    val otherGivenTuples                                                                                                                                 = (givenTuples -- commonTuples).toList.sorted
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
      wordGen      <- ZIO.service[WordGeneratorService]
      givenWord    <- wordGen.wordNormalize(roundWord).mapError(th => GameInternalIssue(th))
      wordInDic    <- wordGen.wordExists(givenWord).mapError(th => GameDictionaryIssue(th))
      _            <- ZIO.cond(wordInDic, (), GameWordNotInDictionary(givenWord))
      _            <- ZIO.cond(!board.isOver, (), GameIsOver())
      _            <- ZIO.cond(givenWord.size == hiddenWord.size, (), GamePlayInvalidSize(givenWord))
      newRows       = computeRow(hiddenWord, givenWord) :: board.rows
      newCurrentRow = computeCurrentRow(newRows)
      newBoard      = board.copy(currentRow = newCurrentRow, rows = newRows)
    yield copy(board = newBoard)

object Game:
  def apply(hiddenWord: String, wordMask: String, maxAttemptsCount: Int = 6): Game =
    val uuid  = UUID.randomUUID()
    val board = Board(wordMask, maxAttemptsCount)
    Game(uuid, hiddenWord, board)
