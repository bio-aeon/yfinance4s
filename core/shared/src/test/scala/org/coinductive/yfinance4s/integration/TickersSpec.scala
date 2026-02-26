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

  test("history should return chart data for all tickers") {
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

  test("history with date range should return chart data") {
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

  test("info should return stock data for all tickers") {
    YFinanceClient.resource[IO](config).use { client =>
      val t = Tickers.of[IO](client, "AAPL", "MSFT")
      t.info.map { results =>
        assertEquals(results.size, 2)
        assertEquals(results(Ticker("AAPL")).symbol, "AAPL")
        assertEquals(results(Ticker("MSFT")).symbol, "MSFT")
      }
    }
  }

  test("financials should return financial statements for all tickers") {
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

  test("dividends should return dividend events for all tickers") {
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

  test("splits should return split events for all tickers") {
    YFinanceClient.resource[IO](config).use { client =>
      val t = Tickers.of[IO](client, "AAPL", "MSFT")
      t.splits(Interval.`1Day`, Range.Max).map { results =>
        assertEquals(results.size, 2)
      }
    }
  }

  test("corporateActions should return combined actions for all tickers") {
    YFinanceClient.resource[IO](config).use { client =>
      val t = Tickers.of[IO](client, "AAPL", "MSFT")
      t.corporateActions(Interval.`1Day`, Range.Max).map { results =>
        assertEquals(results.size, 2)
      }
    }
  }

  test("holdersData should return holders information for all tickers") {
    YFinanceClient.resource[IO](config).use { client =>
      val t = Tickers.of[IO](client, "AAPL", "MSFT")
      t.holdersData.map { results =>
        assertEquals(results.size, 2)
      }
    }
  }

  test("analystData should return analyst information for all tickers") {
    YFinanceClient.resource[IO](config).use { client =>
      val t = Tickers.of[IO](client, "AAPL", "MSFT")
      t.analystData.map { results =>
        assertEquals(results.size, 2)
      }
    }
  }

  test("optionExpirations should return expiration dates for all tickers") {
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

  test("history should fail when any ticker is invalid") {
    YFinanceClient.resource[IO](config).use { client =>
      val t = Tickers.of[IO](client, "AAPL", "INVALIDTICKER123")
      t.history(Interval.`1Day`, Range.`1Month`).attempt.map { result =>
        assert(result.isLeft, "Should fail when any ticker is invalid")
      }
    }
  }

  // --- Error-Tolerant Tests ---

  test("attemptHistory should return Right for valid tickers") {
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

  test("attemptHistory should return Left for invalid tickers without failing") {
    YFinanceClient.resource[IO](config).use { client =>
      val t = Tickers.of[IO](client, "AAPL", "INVALIDTICKER123")
      t.attemptHistory(Interval.`1Day`, Range.`1Month`).map { results =>
        assertEquals(results.size, 2)
        assert(results(Ticker("AAPL")).isRight, "AAPL should succeed")
        assert(results(Ticker("INVALIDTICKER123")).isLeft, "Invalid ticker should fail")
      }
    }
  }

  test("attemptInfo should return Right for valid and Left for invalid") {
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

  test("add then history should include the added ticker") {
    YFinanceClient.resource[IO](config).use { client =>
      val t = Tickers.single[IO](client, Ticker("AAPL")).add(Ticker("MSFT"))
      t.history(Interval.`1Day`, Range.`1Month`).map { results =>
        assertEquals(results.size, 2)
        assert(results.contains(Ticker("AAPL")))
        assert(results.contains(Ticker("MSFT")))
      }
    }
  }

  test("withParallelism should work with data methods") {
    YFinanceClient.resource[IO](config).use { client =>
      val t = Tickers.of[IO](client, "AAPL", "MSFT", "GOOGL").withParallelism(1)
      t.history(Interval.`1Day`, Range.`1Month`).map { results =>
        assertEquals(results.size, 3)
      }
    }
  }
}
