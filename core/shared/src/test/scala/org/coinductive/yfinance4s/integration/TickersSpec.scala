package org.coinductive.yfinance4s.integration

import cats.effect.IO
import munit.CatsEffectSuite
import org.coinductive.yfinance4s.{Tickers, YFinanceClient, YFinanceClientConfig}
import org.coinductive.yfinance4s.models.*

import java.time.{ZoneOffset, ZonedDateTime}
import scala.concurrent.duration.*

class TickersSpec extends CatsEffectSuite {

  val config: YFinanceClientConfig = YFinanceClientConfig(
    connectTimeout = 10.seconds,
    readTimeout = 30.seconds,
    retries = 3
  )

  // --- Fail-Fast Tests ---

  test("returns chart data for all tickers") {
    YFinanceClient.resource[IO](config).use { client =>
      val t = Tickers.of[IO](client, "AAPL", "MSFT")
      t.history(Interval.`1Day`, Range.`1Month`).map { results =>
        assertEquals(results.size, 2)
        assert(results.contains(Ticker("AAPL")))
        assert(results.contains(Ticker("MSFT")))
        results.values.foreach { chart =>
          assert(chart.quotes.nonEmpty, "Each chart should have non-empty quotes")
        }
      }
    }
  }

  test("returns chart data within date range") {
    YFinanceClient.resource[IO](config).use { client =>
      val t = Tickers.of[IO](client, "AAPL", "MSFT")
      val since = ZonedDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
      val until = ZonedDateTime.of(2024, 2, 1, 0, 0, 0, 0, ZoneOffset.UTC)
      t.history(Interval.`1Day`, since, until).map { results =>
        assertEquals(results.size, 2)
        results.values.foreach { chart =>
          assert(chart.quotes.nonEmpty, "Each chart should have non-empty quotes")
        }
      }
    }
  }

  test("returns stock data for all tickers") {
    YFinanceClient.resource[IO](config).use { client =>
      val t = Tickers.of[IO](client, "AAPL", "MSFT")
      t.info.map { results =>
        assertEquals(results.size, 2)
        assertEquals(results(Ticker("AAPL")).symbol, "AAPL")
        assertEquals(results(Ticker("MSFT")).symbol, "MSFT")
      }
    }
  }

  test("returns financial statements for all tickers") {
    YFinanceClient.resource[IO](config).use { client =>
      val t = Tickers.of[IO](client, "AAPL", "MSFT")
      t.financials().map { results =>
        assertEquals(results.size, 2)
        results.values.foreach { financials =>
          assert(financials.incomeStatements.nonEmpty, "Should have income statements")
        }
      }
    }
  }

  test("returns dividend events for all tickers") {
    YFinanceClient.resource[IO](config).use { client =>
      val t = Tickers.of[IO](client, "AAPL", "MSFT")
      t.dividends(Interval.`1Day`, Range.Max).map { results =>
        assertEquals(results.size, 2)
        results.values.foreach { divs =>
          assert(divs.nonEmpty, "Established companies should have dividend history")
        }
      }
    }
  }

  test("returns split events for all tickers") {
    YFinanceClient.resource[IO](config).use { client =>
      val t = Tickers.of[IO](client, "AAPL", "MSFT")
      t.splits(Interval.`1Day`, Range.Max).map { results =>
        assertEquals(results.size, 2)
      }
    }
  }

  test("returns combined corporate actions for all tickers") {
    YFinanceClient.resource[IO](config).use { client =>
      val t = Tickers.of[IO](client, "AAPL", "MSFT")
      t.corporateActions(Interval.`1Day`, Range.Max).map { results =>
        assertEquals(results.size, 2)
      }
    }
  }

  test("returns holders information for all tickers") {
    YFinanceClient.resource[IO](config).use { client =>
      val t = Tickers.of[IO](client, "AAPL", "MSFT")
      t.holdersData.map { results =>
        assertEquals(results.size, 2)
      }
    }
  }

  test("returns analyst information for all tickers") {
    YFinanceClient.resource[IO](config).use { client =>
      val t = Tickers.of[IO](client, "AAPL", "MSFT")
      t.analystData.map { results =>
        assertEquals(results.size, 2)
      }
    }
  }

  test("returns option expiration dates for all tickers") {
    YFinanceClient.resource[IO](config).use { client =>
      val t = Tickers.of[IO](client, "AAPL", "MSFT")
      t.optionExpirations.map { results =>
        assertEquals(results.size, 2)
        results.values.foreach { dates =>
          assert(dates.nonEmpty, "Should have option expiration dates")
        }
      }
    }
  }

  test("fails when any ticker is invalid") {
    YFinanceClient.resource[IO](config).use { client =>
      val t = Tickers.of[IO](client, "AAPL", "INVALIDTICKER123")
      t.history(Interval.`1Day`, Range.`1Month`).attempt.map { result =>
        assert(result.isLeft, "Should fail when any ticker is invalid")
      }
    }
  }

  // --- Error-Tolerant Tests ---

  test("returns Right for valid tickers in error-tolerant mode") {
    YFinanceClient.resource[IO](config).use { client =>
      val t = Tickers.of[IO](client, "AAPL", "MSFT")
      t.attemptHistory(Interval.`1Day`, Range.`1Month`).map { results =>
        assertEquals(results.size, 2)
        results.values.foreach { either =>
          assert(either.isRight, s"Expected Right, got $either")
        }
      }
    }
  }

  test("returns Left for invalid tickers without failing") {
    YFinanceClient.resource[IO](config).use { client =>
      val t = Tickers.of[IO](client, "AAPL", "INVALIDTICKER123")
      t.attemptHistory(Interval.`1Day`, Range.`1Month`).map { results =>
        assertEquals(results.size, 2)
        assert(results(Ticker("AAPL")).isRight, "AAPL should succeed")
        assert(results(Ticker("INVALIDTICKER123")).isLeft, "Invalid ticker should fail")
      }
    }
  }

  test("returns Right for valid and Left for invalid tickers") {
    YFinanceClient.resource[IO](config).use { client =>
      val t = Tickers.of[IO](client, "AAPL", "INVALIDTICKER123")
      t.attemptInfo.map { results =>
        assertEquals(results.size, 2)
        assert(results(Ticker("AAPL")).isRight, "AAPL should succeed")
        assert(results(Ticker("INVALIDTICKER123")).isLeft, "Invalid ticker should fail")
      }
    }
  }

  // --- Chaining Tests ---

  test("includes added ticker in subsequent history call") {
    YFinanceClient.resource[IO](config).use { client =>
      val t = Tickers.single[IO](client, Ticker("AAPL")).add(Ticker("MSFT"))
      t.history(Interval.`1Day`, Range.`1Month`).map { results =>
        assertEquals(results.size, 2)
        assert(results.contains(Ticker("AAPL")))
        assert(results.contains(Ticker("MSFT")))
      }
    }
  }

  test("respects parallelism setting in data methods") {
    YFinanceClient.resource[IO](config).use { client =>
      val t = Tickers.of[IO](client, "AAPL", "MSFT", "GOOGL").withParallelism(1)
      t.history(Interval.`1Day`, Range.`1Month`).map { results =>
        assertEquals(results.size, 3)
      }
    }
  }
}
