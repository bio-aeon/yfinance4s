package io.github.coinductive.yfinance4s.integration

import cats.effect.IO
import munit.CatsEffectSuite
import io.github.coinductive.yfinance4s.{YFinanceClient, YFinanceClientConfig}
import io.github.coinductive.yfinance4s.models.{Interval, Ticker}

import java.time.{ZoneOffset, ZonedDateTime}
import scala.concurrent.duration.*

class ChartsSpec extends CatsEffectSuite {

  override val munitTimeout: Duration = 60.seconds

  private val config: YFinanceClientConfig = YFinanceClientConfig(
    connectTimeout = 10.seconds,
    readTimeout = 30.seconds,
    retries = 1
  )

  test("getChart for VOD.L reads a window in which Yahoo reports a day without data") {
    // Yahoo reports no data for VOD.L on 2026-07-30.
    val since = ZonedDateTime.of(2026, 7, 27, 0, 0, 0, 0, ZoneOffset.UTC)
    val until = ZonedDateTime.of(2026, 8, 1, 0, 0, 0, 0, ZoneOffset.UTC)
    YFinanceClient.resource[IO](config).use { client =>
      client.charts.getChart(Ticker("VOD.L"), Interval.`1Day`, since, until).map { chart =>
        val quotes = chart.getOrElse(fail("expected chart data")).quotes
        assert(quotes.nonEmpty, "the window has trading days")
        quotes.foreach { q =>
          val prices = List(q.open, q.high, q.low, q.close, q.adjclose)
          assert(prices.forall(!_.isNaN), s"expected every price of the ${q.datetime} bar, got $prices")
        }
      }
    }
  }
}
