package fr.janalyse.zwords.webapi.presence

import zio.*
import zio.stream.*

/** Tells the open pages that a game just finished, so that they refresh the leaderboards they show. Carries the language of the game:
  * a page only refreshes the language it shows.
  */
trait GameEvents {
  def gameFinished(languageKey: String): UIO[Unit]

  /** The languages of the games finished from now on, for as long as the stream runs */
  def finishedGames: ZStream[Any, Nothing, String]
}

object GameEvents {
  def gameFinished(languageKey: String): URIO[GameEvents, Unit] = ZIO.serviceWithZIO(_.gameFinished(languageKey))

  val live: ULayer[GameEvents] = ZLayer.fromZIO(
    Hub.sliding[String](64).map(hub => // a slow page only misses some refresh signals, which carry no data
      new GameEvents {
        override def gameFinished(languageKey: String): UIO[Unit]  = hub.publish(languageKey).unit
        override def finishedGames: ZStream[Any, Nothing, String] = ZStream.fromHub(hub)
      }
    )
  )
}
