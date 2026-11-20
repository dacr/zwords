package fr.janalyse.zwords.gamelogic

import com.github.plokhotnyuk.jsoniter_scala.core.{readFromString, writeToString}
import zio.test.*

// The games stored in LMDB were written with zio-json : the jsoniter codecs must keep reading and writing that
// exact format (see fr.janalyse.zwords.json.JsonCodecs), or the games in progress stop decoding after an upgrade.
object GameJsonFormatSpec extends BaseSpecDefault {
  val row = GuessRow(Some("SIMPLE"), List(GoodPlaceCell('S'), WrongPlaceCell('I'), NotUsedCell('M'), EmptyCell()))

  override def spec = suite("game json format, as zio-json wrote it")(
    test("sealed GuessCell as a wrapper object") {
      assertTrue(
        writeToString[GuessCell](GoodPlaceCell('S')) == """{"GoodPlaceCell":{"char":"S"}}""",
        writeToString[GuessCell](EmptyCell()) == """{"EmptyCell":{}}"""
      )
    },
    test("zio-json written cells and rows decode") {
      val zioJsonRow = """{"triedWord":"SIMPLE","state":[{"GoodPlaceCell":{"char":"S"}},{"WrongPlaceCell":{"char":"I"}},{"NotUsedCell":{"char":"M"}},{"EmptyCell":{}}]}"""
      assertTrue(readFromString[GuessRow](zioJsonRow) == row)
    },
    test("empty collections are written, None is omitted") {
      val board = Board(GuessRow(None, List(EmptyCell())), Nil, 6)
      assertTrue(writeToString(board) == """{"patternRow":{"state":[{"EmptyCell":{}}]},"playedRows":[],"maxRowsCount":6}""")
    }
  )
}
