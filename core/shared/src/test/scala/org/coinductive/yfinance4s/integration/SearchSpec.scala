package org.coinductive.yfinance4s.integration

import cats.effect.IO
import munit.CatsEffectSuite
import org.coinductive.yfinance4s.{YFinanceClient, YFinanceClientConfig}
import org.coinductive.yfinance4s.models.Ticker

import scala.concurrent.duration.*

class SearchSpec extends CatsEffectSuite {

  val config: YFinanceClientConfig = YFinanceClientConfig(
    connectTimeout = 10.seconds,
    readTimeout = 30.seconds,
    retries = 3
  )

  test("returns quotes for well-known company name") {
    YFinanceClient.resource[IO](config).use { client =>
      client.search("Apple").map { result =>
        assert(result.nonEmpty, "Search for 'Apple' should return results")
        assert(result.quotes.nonEmpty, "Should have quote results")
        assert(
          result.quotes.exists(_.symbol == "AAPL"),
          s"AAPL should appear in results: ${result.symbols}"
        )
      }
    }
  }

  test("returns quotes for ticker symbol") {
    YFinanceClient.resource[IO](config).use { client =>
      client.search("MSFT").map { result =>
        assert(result.quotes.nonEmpty, "Search for 'MSFT' should return quotes")
        assert(
          result.quotes.exists(_.symbol == "MSFT"),
          s"MSFT should appear in results: ${result.symbols}"
        )
        val msft = result.quotes.find(_.symbol == "MSFT").get
        assert(msft.isEquity, "MSFT should be an equity")
      }
    }
  }

  test("returns news items with valid structure") {
    YFinanceClient.resource[IO](config).use { client =>
      client.search("Tesla", newsCount = 3).map { result =>
        result.news.foreach { newsItem =>
          assert(newsItem.title.nonEmpty, "News title should not be empty")
          assert(newsItem.publisher.nonEmpty, "News publisher should not be empty")
          assert(newsItem.link.nonEmpty, "News link should not be empty")
          assert(newsItem.uuid.nonEmpty, "News UUID should not be empty")
        }
      }
    }
  }

  test("returns quote results with valid structure") {
    YFinanceClient.resource[IO](config).use { client =>
      client.search("NVDA").map { result =>
        assert(result.quotes.nonEmpty, "Should have quote results for NVDA")
        val nvda = result.quotes.find(_.symbol == "NVDA")
        assert(nvda.isDefined, "NVDA should be in results")
        val q = nvda.get
        assert(q.symbol == "NVDA", "Symbol should be NVDA")
        assert(q.exchangeDisplay.isDefined, "Exchange display should be defined")
        assert(q.displayName.nonEmpty, "Display name should not be empty")
        assert(q.toTicker == Ticker("NVDA"), "toTicker should produce correct Ticker")
      }
    }
  }

  test("respects maxResults parameter") {
    YFinanceClient.resource[IO](config).use { client =>
      client.search("Bank", maxResults = 3).map { result =>
        assert(result.quotes.size <= 3, s"Should have at most 3 quotes, got ${result.quotes.size}")
      }
    }
  }

  test("filters out non-Yahoo entities") {
    YFinanceClient.resource[IO](config).use { client =>
      client.search("Apple").map { result =>
        result.quotes.foreach { q =>
          assert(q.symbol.nonEmpty, s"All quotes should have a symbol, found: $q")
        }
      }
    }
  }

  test("returns empty results for nonsense query") {
    YFinanceClient.resource[IO](config).use { client =>
      client.search("xyznonexistentticker12345").map { result =>
        assert(result.quotes.isEmpty, "Should have no quote results for nonsense query")
      }
    }
  }

  test("returns results for misspelled queries when fuzzy query enabled") {
    YFinanceClient.resource[IO](config).use { client =>
      client.search("Aple", enableFuzzyQuery = true).map { result =>
        assert(result.quotes.nonEmpty, "Fuzzy search for 'Aple' should return results")
      }
    }
  }

  test("returns results for broad query") {
    YFinanceClient.resource[IO](config).use { client =>
      client.search("S&P 500").map { result =>
        assert(result.quotes.nonEmpty, "Search for 'S&P 500' should return results")
        val types = result.quotes.flatMap(_.quoteType).distinct
        assert(types.nonEmpty, s"Should have type information: $types")
      }
    }
  }

  test("provides symbols, tickers, and topQuote convenience accessors") {
    YFinanceClient.resource[IO](config).use { client =>
      client.search("Amazon").map { result =>
        assert(result.nonEmpty, "Should have results")
        assert(result.symbols.nonEmpty, "symbols should not be empty")
        assert(result.tickers.nonEmpty, "tickers should not be empty")

        val top = result.topQuote
        assert(top.isDefined, "topQuote should be defined")
      }
    }
  }
}
