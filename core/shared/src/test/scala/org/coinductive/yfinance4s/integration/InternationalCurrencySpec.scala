package org.coinductive.yfinance4s.integration

import cats.effect.IO
import munit.CatsEffectSuite
import org.coinductive.yfinance4s.{YFinanceClient, YFinanceClientConfig}
import org.coinductive.yfinance4s.models.{Interval, PriceRepairConfig, Range, Ticker}

import java.time.{ZoneOffset, ZonedDateTime}
import scala.concurrent.duration.*

class InternationalCurrencySpec extends CatsEffectSuite {

  override val munitTimeout: Duration = 60.seconds

  private val config: YFinanceClientConfig = YFinanceClientConfig(
    connectTimeout = 10.seconds,
    readTimeout = 30.seconds,
    retries = 1
  )

  private val penceQuoted = Ticker("VOD.L")

  private def median(values: List[Double]): Double = {
    val sorted = values.sorted
    if (sorted.isEmpty) Double.NaN
    else if (sorted.size % 2 == 1) sorted(sorted.size / 2)
    else (sorted(sorted.size / 2 - 1) + sorted(sorted.size / 2)) / 2.0
  }

  test("standardises a pence-quoted chart to pounds") {
    // A fixed, fully-elapsed calendar window: both fetches see immutable bars.
    val since = ZonedDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
    val until = ZonedDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
    YFinanceClient.resource[IO](config).use { client =>
      for {
        raw <- client.charts.getChart(penceQuoted, Interval.`1Day`, since, until)
        standardised <- client.charts.getChart(penceQuoted, Interval.`1Day`, since, until, PriceRepairConfig.Enabled)
      } yield {
        val rawChart = raw.getOrElse(fail("expected raw data"))
        val standardisedChart = standardised.getOrElse(fail("expected standardised data"))
        assertEquals(rawChart.currency, "GBp")
        assertEquals(standardisedChart.currency, "GBP")
        // Median tolerates any individually repaired bars in either series.
        val ratios = rawChart.quotes.map(_.close).zip(standardisedChart.quotes.map(_.close)).collect {
          case (rawClose, standardisedClose) if standardisedClose > 0.0 => rawClose / standardisedClose
        }
        val ratio = median(ratios)
        assert(ratio > 90.0 && ratio < 110.0, s"expected pence to be ~100x the pounds series, got $ratio")
      }
    }
  }

  test("reports the trading currency without repair") {
    YFinanceClient.resource[IO](config).use { client =>
      client.charts.getChart(Ticker("AAPL"), Interval.`1Day`, Range.`1Year`).map { chart =>
        assertEquals(chart.map(_.currency), Some("USD"))
      }
    }
  }

  test("repair with conversion enabled never fails an international fetch") {
    // The equity window is fixed and fully elapsed: a live range can include a null holiday bar, which
    // the chart decoder rejects before repair is ever reached (a known limitation, unrelated to repair).
    val since = ZonedDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
    val until = ZonedDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
    YFinanceClient.resource[IO](config).use { client =>
      for {
        equity <- client.charts.getChart(penceQuoted, Interval.`1Day`, since, until, PriceRepairConfig.Enabled)
        fx <- client.charts.getChart(Ticker("GBPUSD=X"), Interval.`1Day`, Range.`1Month`, PriceRepairConfig.Enabled)
      } yield {
        assert(equity.exists(_.quotes.nonEmpty), "the equity fetch should return bars")
        assert(fx.exists(_.quotes.nonEmpty), "the FX fetch should return bars")
      }
    }
  }
}
