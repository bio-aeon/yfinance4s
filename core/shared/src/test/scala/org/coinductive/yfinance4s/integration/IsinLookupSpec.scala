package org.coinductive.yfinance4s.integration

import cats.effect.IO
import cats.syntax.traverse.*
import munit.CatsEffectSuite
import org.coinductive.yfinance4s.{YFinanceClient, YFinanceClientConfig}
import org.coinductive.yfinance4s.models.Ticker

import scala.concurrent.duration.*

class IsinLookupSpec extends CatsEffectSuite {

  val config: YFinanceClientConfig = YFinanceClientConfig(
    connectTimeout = 10.seconds,
    readTimeout = 30.seconds,
    retries = 3
  )

  // --- lookupByISIN ---

  test("resolves well-known ISINs to expected tickers") {
    val isinToTicker = List(
      "US0378331005" -> Ticker("AAPL"),
      "US5949181045" -> Ticker("MSFT")
    )
    YFinanceClient.resource[IO](config).use { client =>
      isinToTicker.traverse { case (isin, expected) =>
        client.lookupByISIN(isin).map { result =>
          assert(result.isDefined, s"ISIN $isin should resolve")
          assertEquals(result.get, expected)
        }
      }.void
    }
  }

  test("resolves international ISINs") {
    val isinToSubstring = List(
      "GB0002374006" -> "DGE", // Diageo (UK)
      "DE0007164600" -> "SAP" // SAP (Germany)
    )
    YFinanceClient.resource[IO](config).use { client =>
      isinToSubstring.traverse { case (isin, substring) =>
        client.lookupByISIN(isin).map { result =>
          assert(result.isDefined, s"ISIN $isin should resolve")
          assert(
            result.get.value.contains(substring),
            s"Expected ticker containing '$substring' for $isin, got: ${result.get.value}"
          )
        }
      }.void
    }
  }

  test("returns None for valid but nonexistent ISIN") {
    // US0000000002 has a valid check digit (0) but no real security
    YFinanceClient.resource[IO](config).use { client =>
      client.lookupByISIN("US0000000002").map { result =>
        assert(result.isEmpty, s"Expected None for nonexistent ISIN, got: $result")
      }
    }
  }

  test("raises error for malformed ISIN") {
    YFinanceClient.resource[IO](config).use { client =>
      interceptIO[IllegalArgumentException](client.lookupByISIN("INVALID"))
    }
  }

  test("raises error for ISIN with wrong check digit") {
    YFinanceClient.resource[IO](config).use { client =>
      interceptIO[IllegalArgumentException](client.lookupByISIN("US0378331009"))
    }
  }

  test("handles lowercase ISIN input") {
    YFinanceClient.resource[IO](config).use { client =>
      client.lookupByISIN("us0378331005").map { result =>
        assert(result.isDefined, "Lowercase Apple ISIN should resolve")
        assertEquals(result.get, Ticker("AAPL"))
      }
    }
  }

  // --- lookupAllByISIN ---

  test("returns results with quote details for widely-traded ISIN") {
    YFinanceClient.resource[IO](config).use { client =>
      client.lookupAllByISIN("US0378331005").map { results =>
        assert(results.nonEmpty, "Apple ISIN should return at least one result")
        assert(results.exists(_.symbol == "AAPL"), s"AAPL should appear in results: ${results.map(_.symbol)}")
        val first = results.head
        assert(first.symbol.nonEmpty, "symbol should not be empty")
        assert(first.exchangeDisplay.isDefined, "exchangeDisplay should be defined")
        assert(first.quoteType.isDefined, "quoteType should be defined")
      }
    }
  }

  test("returns empty list for valid but nonexistent ISIN") {
    YFinanceClient.resource[IO](config).use { client =>
      client.lookupAllByISIN("US0000000002").map { results =>
        assert(results.isEmpty, s"Expected empty list for nonexistent ISIN, got: ${results.map(_.symbol)}")
      }
    }
  }
}
