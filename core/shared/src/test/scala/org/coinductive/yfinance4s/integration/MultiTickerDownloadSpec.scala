package org.coinductive.yfinance4s.integration

import cats.data.NonEmptyList
import cats.effect.IO
import munit.CatsEffectSuite
import org.coinductive.yfinance4s.{YFinanceClient, YFinanceClientConfig}
import org.coinductive.yfinance4s.models.*

import java.time.{ZoneOffset, ZonedDateTime}
import scala.concurrent.duration.*

class MultiTickerDownloadSpec extends CatsEffectSuite {

  val config: YFinanceClientConfig = YFinanceClientConfig(
    connectTimeout = 10.seconds,
    readTimeout = 30.seconds,
    retries = 3
  )

  test("downloadCharts should return data for multiple valid tickers") {
    YFinanceClient.resource[IO](config).use { client =>
      val tickers = NonEmptyList.of(Ticker("AAPL"), Ticker("MSFT"), Ticker("GOOGL"))

      client.downloadCharts(tickers, Interval.`1Day`, Range.`1Month`).map { results =>
        assertEquals(results.size, 3)
        assert(results.contains(Ticker("AAPL")))
        assert(results.contains(Ticker("MSFT")))
        assert(results.contains(Ticker("GOOGL")))
        results.values.foreach { chart =>
          assert(chart.quotes.nonEmpty, "Each chart should have non-empty quotes")
        }
      }
    }
  }

  test("downloadCharts with date range should return data") {
    YFinanceClient.resource[IO](config).use { client =>
      val tickers = NonEmptyList.of(Ticker("AAPL"), Ticker("MSFT"))
      val since = ZonedDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
      val until = ZonedDateTime.of(2024, 2, 1, 0, 0, 0, 0, ZoneOffset.UTC)

      client.downloadCharts(tickers, Interval.`1Day`, since, until, parallelism = 4).map { results =>
        assertEquals(results.size, 2)
        results.values.foreach { chart =>
          assert(chart.quotes.nonEmpty, "Each chart should have non-empty quotes")
        }
      }
    }
  }

  test("downloadCharts should fail when any ticker is invalid") {
    YFinanceClient.resource[IO](config).use { client =>
      val tickers = NonEmptyList.of(Ticker("AAPL"), Ticker("INVALIDTICKER123"))

      client.downloadCharts(tickers, Interval.`1Day`, Range.`1Month`).attempt.map { result =>
        assert(result.isLeft, "Should fail when any ticker is invalid")
      }
    }
  }

  test("downloadCharts should respect parallelism parameter") {
    YFinanceClient.resource[IO](config).use { client =>
      val tickers = NonEmptyList.of(
        Ticker("AAPL"),
        Ticker("MSFT"),
        Ticker("GOOGL"),
        Ticker("AMZN"),
        Ticker("NVDA")
      )

      client.downloadCharts(tickers, Interval.`1Day`, Range.`1Month`, parallelism = 2).map { results =>
        assertEquals(results.size, 5)
      }
    }
  }

  test("downloadStocks should return data for multiple valid tickers") {
    YFinanceClient.resource[IO](config).use { client =>
      val tickers = NonEmptyList.of(Ticker("AAPL"), Ticker("MSFT"))

      client.downloadStocks(tickers).map { results =>
        assertEquals(results.size, 2)
        assertEquals(results(Ticker("AAPL")).symbol, "AAPL")
        assertEquals(results(Ticker("MSFT")).symbol, "MSFT")
      }
    }
  }

  test("downloadStocks should fail when any ticker is invalid") {
    YFinanceClient.resource[IO](config).use { client =>
      val tickers = NonEmptyList.of(Ticker("AAPL"), Ticker("INVALIDTICKER123"))

      client.downloadStocks(tickers).attempt.map { result =>
        assert(result.isLeft, "Should fail when any ticker is invalid")
      }
    }
  }

  test("downloadFinancialStatements should return data for multiple tickers") {
    YFinanceClient.resource[IO](config).use { client =>
      val tickers = NonEmptyList.of(Ticker("AAPL"), Ticker("MSFT"))

      client.downloadFinancialStatements(tickers).map { results =>
        assertEquals(results.size, 2)
        results.values.foreach { financials =>
          assert(financials.incomeStatements.nonEmpty, "Should have income statements")
        }
      }
    }
  }

  test("downloadCharts for single ticker in NonEmptyList should work") {
    YFinanceClient.resource[IO](config).use { client =>
      val tickers = NonEmptyList.of(Ticker("AAPL"))

      client.downloadCharts(tickers, Interval.`1Day`, Range.`1Month`).map { results =>
        assertEquals(results.size, 1)
        assert(results(Ticker("AAPL")).quotes.nonEmpty)
      }
    }
  }
}
