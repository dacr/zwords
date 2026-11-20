package fr.janalyse.zwords.webapi

import fr.janalyse.zwords.webapi.presence.PresenceService
import zio.*
import zio.test.*

import java.util.UUID

object PresenceServiceSpec extends BaseSpecDefault {

  def countReaches(expected: Int) = PresenceService.onlineCount.repeatUntil(_ == expected)

  override def spec = suite("players online")(
    test("a player is counted once whatever the number of open pages, closed pages are forgotten") {
      val player = UUID.randomUUID()
      for {
        page1     <- PresenceService.connect(Some(player)).runDrain.fork
        page2     <- PresenceService.connect(Some(player)).runDrain.fork
        anonymous <- PresenceService.connect(None).runDrain.fork
        both      <- countReaches(2)
        _         <- page1.interrupt
        stillBoth <- PresenceService.onlineCount
        _         <- page2.interrupt
        one       <- PresenceService.onlineCount
        _         <- anonymous.interrupt
        none      <- PresenceService.onlineCount
      } yield assertTrue(both == 2, stillBoth == 2, one == 1, none == 0)
    },
    test("a page receives the current count first, then every change") {
      for {
        watcher <- PresenceService.connect(None).take(3).runCollect.fork
        _       <- countReaches(1)
        other   <- PresenceService.connect(Some(UUID.randomUUID())).runDrain.fork
        _       <- countReaches(2)
        _       <- other.interrupt
        seen    <- watcher.join
        after   <- PresenceService.onlineCount
      } yield assertTrue(seen == Chunk(1, 2, 1), after == 0)
    }
  ).provide(PresenceService.live) @@ TestAspect.sequential @@ TestAspect.timeout(30.seconds)
}
