package fr.janalyse.zwords.console

import fr.janalyse.zwords.gamelogic.{Game, Word}
import zio.*

object Main extends ZIOAppDefault {

  override def run: ZIO[Any, Any, Any] =
    val game = Game(Word("REPTILE"))


}
