package org.coinductive.yfinance4s.integration

import cats.effect.IO
import munit.CatsEffectSuite
import org.coinductive.yfinance4s.{YFinanceClient, YFinanceClientConfig}
import org.coinductive.yfinance4s.models.{Interval, PriceRepairConfig, Range, Ticker}

import java.time.{ZoneOffset, ZonedDateTime}
import scala.concurrent.duration.*

class PriceRepairSpec extends CatsEffectSuite {

  override val munitTimeout: Duration = 60.seconds

  private val config: YFinanceClientConfig = YFinanceClientConfig(
    connectTimeout = 10.seconds,
    readTimeout = 30.seconds,
    retries = 1
  )

  test("getChart with repair enabled succeeds and never fails the request") {
    YFinanceClient.resource[IO](config).use { client =>
      client.charts.getChart(Ticker("AAPL"), Interval.`1Day`, Range.`1Year`, PriceRepairConfig.Enabled).map {
        chartOpt =>
          assert(chartOpt.isDefined, "chart should be returned with repair enabled")
          assert(chartOpt.exists(_.quotes.nonEmpty), "chart should contain bars")
      }
    }
  }

  test("repair does not alter a clean fully-elapsed window") {
    // A fixed, fully-elapsed calendar window: both fetches see immutable bars, so the comparison is
    // deterministic (a live range would differ on the still-forming last bar).
    val since = ZonedDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
    val until = ZonedDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
    YFinanceClient.resource[IO](config).use { client =>
      for {
        repaired <- client.charts.getChart(Ticker("AAPL"), Interval.`1Day`, since, until, PriceRepairConfig.Enabled)
        raw <- client.charts.getChart(Ticker("AAPL"), Interval.`1Day`, since, until)
      } yield {
        assert(repaired.isDefined && raw.isDefined, "both fetches should return data")
        assertEquals(repaired.map(_.quotes.map(_.close)), raw.map(_.quotes.map(_.close)))
      }
    }
  }
}
