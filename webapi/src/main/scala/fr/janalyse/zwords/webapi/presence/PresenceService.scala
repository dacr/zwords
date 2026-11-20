package fr.janalyse.zwords.webapi.presence

import zio.*
import zio.stream.*

import java.util.UUID

/** Counts the players who have the game open, for the "players online" indicator of the user interface.
  *
  * Every open page holds one stream (server-sent events). A player is counted once whatever the number of pages (tabs, devices) they have
  * open; a page that doesn't give its player is counted on its own. The count lives in memory only: it starts from zero when the service
  * starts, and the pages reconnect by themselves.
  */
trait PresenceService {

  /** Registers a connection for as long as the returned stream runs, and streams the online count: the current one first, then every
    * change. Ending the stream (the client went away) unregisters the connection.
    */
  def connect(playerId: Option[UUID]): ZStream[Any, Nothing, Int]

  def onlineCount: UIO[Int]
}

object PresenceService {
  def connect(playerId: Option[UUID]): ZStream[PresenceService, Nothing, Int] = ZStream.serviceWithStream(_.connect(playerId))
  def onlineCount: URIO[PresenceService, Int]                                 = ZIO.serviceWithZIO(_.onlineCount)

  val live: ULayer[PresenceService] = ZLayer.fromZIO(
    for {
      connections <- Ref.make(Map.empty[String, Int]) // connection key -> number of open connections
      updates     <- Hub.sliding[Int](16)             // only the latest counts matter to a slow subscriber
      anonymous   <- Ref.make(0L)
    } yield PresenceServiceLive(connections, updates, anonymous)
  )
}

case class PresenceServiceLive(connections: Ref[Map[String, Int]], updates: Hub[Int], anonymous: Ref[Long]) extends PresenceService {

  override def onlineCount: UIO[Int] = connections.get.map(_.size)

  private def publish(state: Map[String, Int]): UIO[Unit] = updates.publish(state.size).unit

  private def register(key: String): UIO[Unit] =
    connections.updateAndGet(state => state.updated(key, state.getOrElse(key, 0) + 1)).flatMap(publish)

  private def unregister(key: String): UIO[Unit] =
    connections
      .updateAndGet(state =>
        state.get(key) match {
          case Some(count) if count > 1 => state.updated(key, count - 1)
          case _                        => state.removed(key)
        }
      )
      .flatMap(publish)

  override def connect(playerId: Option[UUID]): ZStream[Any, Nothing, Int] =
    ZStream.unwrapScoped {
      for {
        key     <- playerId match {
                     case Some(id) => ZIO.succeed(id.toString)
                     case None     => anonymous.updateAndGet(_ + 1).map(n => s"anonymous-$n")
                   }
        changes <- updates.subscribe // subscribed before registering, so no change is missed
        _       <- ZIO.acquireRelease(register(key))(_ => unregister(key))
        current <- onlineCount
      } yield (ZStream.succeed(current) ++ ZStream.fromQueue(changes)).changes
    }
}
