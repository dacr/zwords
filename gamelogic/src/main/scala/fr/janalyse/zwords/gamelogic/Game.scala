package fr.janalyse.zwords.gamelogic

import fr.janalyse.zwords.*
import fr.janalyse.zwords.dictionary.DictionaryService
import zio.*

import java.util.UUID
import scala.collection.LazyZip2
import scala.io.AnsiColor.*

case class Word(text: String):
  val normalized = text.toUpperCase
  def size = text.size

sealed trait GuessCell

case class GoodPlaceCell(char: Char) extends GuessCell:
  override def toString: String = s"$GREEN_B$char$RESET"

case class NotUsedCell(char: Char) extends GuessCell:
  override def toString: String = s"$char"

case class WrongPlaceCell(char: Char) extends GuessCell:
  override def toString: String = s"$RED_B$char$RESET"

case class EmptyCell() extends GuessCell:
  override def toString: String = "."

case class GuessRow(state: List[GuessCell]):
  override def toString = state.mkString

case class Board(currentRow: GuessRow, rows: List[GuessRow], maxRowsCount: Int):
  def isWin: Boolean    = currentRow.state.forall(_.isInstanceOf[GoodPlaceCell])
  def isOver: Boolean   = isWin || rows.size >= maxRowsCount
  override def toString = (currentRow :: rows).reverse.mkString("\n")

object Board:
  def apply(hiddenWord: Word, maxAttemptsCount: Int, showFirst: Boolean): Board =
    val chars      = hiddenWord.normalized.toList
    val initialRow =
      if showFirst then GuessRow(GoodPlaceCell(chars.head) :: chars.tail.map(_ => EmptyCell()))
      else GuessRow(chars.map(_ => EmptyCell()))
    Board(initialRow, Nil, maxAttemptsCount)

sealed trait GameIssue
case class GameIsOver()                              extends GameIssue
case class GamePlayInvalidInput(word: Word)          extends GameIssue
case class GameWordNotInDictionary(word: Word)       extends GameIssue
case class GameDictionaryIssue(throwable: Throwable) extends GameIssue

case class Game(private val uuid: UUID, private val hiddenWord: Word, board: Board):

  def computeRow(hiddenWord: Word, givenWord: Word): GuessRow =
    val hiddenTuples                                                                                                                                     = hiddenWord.normalized.zipWithIndex.map(_.swap).toSet
    val givenTuples                                                                                                                                      = givenWord.normalized.zipWithIndex.map(_.swap).toSet
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

  def play(givenWord: Word): ZIO[DictionaryService, GameIssue, Game] =
    for
      dico         <- ZIO.service[DictionaryService]
      wordInDic    <- dico.wordExists(givenWord.text).mapError(th => GameDictionaryIssue(th))
      _            <- ZIO.cond(wordInDic, (), GameWordNotInDictionary(givenWord))
      _            <- ZIO.cond(!board.isOver, (), GameIsOver())
      _            <- ZIO.cond(givenWord.size != hiddenWord.size, (), GamePlayInvalidInput(givenWord))
      newRows       = computeRow(hiddenWord, givenWord) :: board.rows
      newCurrentRow = computeCurrentRow(newRows)
      newBoard      = board.copy(currentRow = newCurrentRow, rows = newRows)
    yield copy(board = newBoard)

object Game:
  def apply(hiddenWord: Word, maxAttemptsCount: Int = 6, showFirst: Boolean = true): Game =
    val uuid  = UUID.randomUUID()
    val board = Board(hiddenWord, maxAttemptsCount, showFirst)
    Game(uuid, hiddenWord, board)
