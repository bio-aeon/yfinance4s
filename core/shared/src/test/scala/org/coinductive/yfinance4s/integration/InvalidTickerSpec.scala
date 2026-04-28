package org.coinductive.yfinance4s.integration

import cats.data.NonEmptyList
import cats.effect.IO
import munit.CatsEffectSuite
import org.coinductive.yfinance4s.{Tickers, YFinanceClient, YFinanceClientConfig}
import org.coinductive.yfinance4s.models.*

import scala.concurrent.duration.*

class InvalidTickerSpec extends CatsEffectSuite {

  override val munitTimeout: Duration = 60.seconds

  private val config: YFinanceClientConfig = YFinanceClientConfig(
    connectTimeout = 10.seconds,
    readTimeout = 30.seconds,
    retries = 1
  )

  private val invalidTicker: Ticker = Ticker("INVALIDTICKER123")
  private val validTicker: Ticker = Ticker("AAPL")

  test("Charts.getChart on an invalid ticker raises YFinanceError.TickerNotFound") {
    YFinanceClient.resource[IO](config).use { client =>
      client.charts.getChart(invalidTicker, Interval.`1Day`, Range.`1Month`).attempt.map {
        case Left(YFinanceError.TickerNotFound(t)) => assertEquals(t, invalidTicker)
        case other                                 => fail(s"expected TickerNotFound($invalidTicker), got $other")
      }
    }
  }

  test("downloadCharts on an invalid ticker raises YFinanceError.TickerNotFound") {
    YFinanceClient.resource[IO](config).use { client =>
      client
        .downloadCharts(NonEmptyList.of(invalidTicker), Interval.`1Day`, Range.`1Month`)
        .attempt
        .map {
          case Left(YFinanceError.TickerNotFound(t)) => assertEquals(t, invalidTicker)
          case other                                 => fail(s"expected TickerNotFound($invalidTicker), got $other")
        }
    }
  }

  test("Tickers.attemptHistory returns Right for valid and Left(TickerNotFound) for invalid") {
    YFinanceClient.resource[IO](config).use { client =>
      val tickers = Tickers.of[IO](client, validTicker.value, invalidTicker.value)
      tickers.attemptHistory(Interval.`1Day`, Range.`1Month`).map { results =>
        assertEquals(results.size, 2)
        assert(results(validTicker).isRight, s"valid ticker should succeed, got ${results(validTicker)}")
        results(invalidTicker) match {
          case Left(YFinanceError.TickerNotFound(t)) => assertEquals(t, invalidTicker)
          case other => fail(s"expected Left(TickerNotFound($invalidTicker)), got $other")
        }
      }
    }
  }
}
