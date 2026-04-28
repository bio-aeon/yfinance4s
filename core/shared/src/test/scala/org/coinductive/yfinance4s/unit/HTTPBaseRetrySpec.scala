package org.coinductive.yfinance4s.unit

import cats.effect.IO
import cats.effect.kernel.Ref
import cats.effect.testkit.TestControl
import cats.syntax.all.*
import munit.CatsEffectSuite
import org.coinductive.yfinance4s.HTTPBase
import org.coinductive.yfinance4s.models.{Ticker, YFinanceError}
import retry.{RetryPolicies, retryingOnSomeErrors}

import scala.concurrent.duration.*

class HTTPBaseRetrySpec extends CatsEffectSuite {

  private val sampleTicker: Ticker = Ticker("FOO")
  private val MaxRetries: Int = 3

  private def runRetried[A](action: IO[A]): IO[Either[Throwable, A]] =
    retryingOnSomeErrors(
      policy = RetryPolicies.limitRetries[IO](MaxRetries),
      isWorthRetrying = HTTPBase.isRetriable[IO],
      onError = HTTPBase.onErrorSleep[IO]
    )(action).attempt

  private def countingAction[A](counter: Ref[IO, Int])(body: Int => IO[A]): IO[A] =
    counter.updateAndGet(_ + 1).flatMap(body)

  test("permanent errors (TickerNotFound) are not retried") {
    val program = for {
      counter <- Ref.of[IO, Int](0)
      _ <- runRetried(countingAction(counter)(_ => IO.raiseError(YFinanceError.TickerNotFound(sampleTicker))))
      attempts <- counter.get
    } yield attempts

    program.assertEquals(1)
  }

  test("transient errors (NetworkError) are retried up to the configured limit") {
    val program = for {
      counter <- Ref.of[IO, Int](0)
      _ <- runRetried(
        countingAction(counter)(_ => IO.raiseError(YFinanceError.NetworkError("transient", None)))
      )
      attempts <- counter.get
    } yield attempts

    program.assertEquals(MaxRetries + 1)
  }

  test("unknown throwables are retried by default") {
    val program = for {
      counter <- Ref.of[IO, Int](0)
      _ <- runRetried(countingAction(counter)(_ => IO.raiseError(new RuntimeException("unknown"))))
      attempts <- counter.get
    } yield attempts

    program.assertEquals(MaxRetries + 1)
  }

  test("RateLimited triggers a sleep equal to the retry-after duration before the next attempt") {
    val retryAfter = 5.seconds

    val program = for {
      counter <- Ref.of[IO, Int](0)
      start <- IO.monotonic
      _ <- runRetried(countingAction(counter) { count =>
        if (count == 1) IO.raiseError(YFinanceError.RateLimited(Some(retryAfter)))
        else IO.unit
      })
      end <- IO.monotonic
    } yield end - start

    TestControl.executeEmbed(program).assertEquals(retryAfter)
  }
}
