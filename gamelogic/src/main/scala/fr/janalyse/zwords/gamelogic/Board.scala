package fr.janalyse.zwords.gamelogic

import zio.json.{DeriveJsonCodec, JsonCodec}

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
