package org.coinductive.yfinance4s.integration

import cats.data.NonEmptyList
import cats.effect.IO
import munit.CatsEffectSuite
import org.coinductive.yfinance4s.{YFinanceClient, YFinanceClientConfig}
import org.coinductive.yfinance4s.models.*

import scala.concurrent.duration.*

class RateLimitedClientSpec extends CatsEffectSuite {

  override val munitTimeout: Duration = 120.seconds

  private val baseConfig: YFinanceClientConfig = YFinanceClientConfig(
    connectTimeout = 10.seconds,
    readTimeout = 30.seconds,
    retries = 3
  )

  private val tickers: NonEmptyList[Ticker] =
    NonEmptyList.of(Ticker("AAPL"), Ticker("MSFT"), Ticker("GOOGL"), Ticker("NVDA"))

  test("respects rate limit when downloading multiple tickers") {
    val maxRequestsPerSecond = 2
    val config = baseConfig.copy(rateLimit = RateLimitConfig.Enabled(maxRequestsPerSecond))

    YFinanceClient.resource[IO](config).use { client =>
      for {
        start <- IO.monotonic
        _ <- client.downloadCharts(tickers, Interval.`1Day`, Range.`1Month`, parallelism = tickers.size)
        end <- IO.monotonic
        elapsed = end - start
      } yield {
        // (n - 1) * minInterval is the lower bound for sequenced starts.
        val lowerBound = (tickers.size - 1) * (1.second / maxRequestsPerSecond.toLong)
        assert(
          elapsed >= lowerBound,
          s"expected elapsed >= $lowerBound under ${maxRequestsPerSecond} RPS, got $elapsed"
        )
      }
    }
  }

  test("Disabled config does not introduce extra latency") {
    val config = baseConfig.copy(rateLimit = RateLimitConfig.Disabled)

    YFinanceClient.resource[IO](config).use { client =>
      for {
        start <- IO.monotonic
        _ <- client.downloadCharts(tickers, Interval.`1Day`, Range.`1Month`, parallelism = tickers.size)
        end <- IO.monotonic
        elapsed = end - start
      } yield {
        // Sanity check: 4 small chart fetches with no pacing should easily complete in 30s.
        assert(elapsed < 30.seconds, s"Disabled rate limiting should not throttle, got $elapsed")
      }
    }
  }

  test("default client config produces a working client") {
    YFinanceClient.resource[IO](baseConfig).use { client =>
      client.charts.getStock(Ticker("AAPL")).map { result =>
        assert(result.isDefined, "AAPL stock should be retrievable with default config")
      }
    }
  }
}
