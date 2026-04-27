package org.coinductive.yfinance4s.unit

import cats.effect.IO
import cats.effect.testkit.TestControl
import cats.syntax.all.*
import munit.CatsEffectSuite
import org.coinductive.yfinance4s.RateLimiter
import org.coinductive.yfinance4s.models.RateLimitConfig

import scala.concurrent.duration.*

class RateLimiterSpec extends CatsEffectSuite {

  test("Enabled config paces successive acquires at the configured interval") {
    val maxRequestsPerSecond = 4
    val acquires = 5
    val expectedInterval = 1.second / maxRequestsPerSecond.toLong

    val program = RateLimiter.resource[IO](RateLimitConfig.Enabled(maxRequestsPerSecond)).use { limiter =>
      (1 to acquires).toList.traverse(_ => limiter.acquire(IO.monotonic))
    }

    TestControl.executeEmbed(program).map { times =>
      val deltas = times.sliding(2).collect { case List(a, b) => b - a }.toList
      deltas.foreach(d => assertEquals(d, expectedInterval))
    }
  }

  test("Disabled config never delays an acquire") {
    val program = RateLimiter.resource[IO](RateLimitConfig.Disabled).use { limiter =>
      for {
        start <- IO.monotonic
        _ <- (1 to 100).toList.traverse_(_ => limiter.acquire(IO.unit))
        end <- IO.monotonic
      } yield end - start
    }

    TestControl.executeEmbed(program).assertEquals(Duration.Zero)
  }
}
