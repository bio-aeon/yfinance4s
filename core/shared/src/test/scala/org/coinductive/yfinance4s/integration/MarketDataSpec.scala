package org.coinductive.yfinance4s.integration

import cats.effect.IO
import cats.syntax.all.*
import munit.CatsEffectSuite
import org.coinductive.yfinance4s.models.*
import org.coinductive.yfinance4s.{YFinanceClient, YFinanceClientConfig}

import scala.concurrent.duration.*

class MarketDataSpec extends CatsEffectSuite {

  private val config: YFinanceClientConfig = YFinanceClientConfig(
    connectTimeout = 10.seconds,
    readTimeout = 30.seconds,
    retries = 3
  )

  private val US = MarketRegion("US")
  private val GB = MarketRegion("GB")
  private val majorRegions = List("US", "GB", "JP", "DE").map(MarketRegion(_))

  // --- getSummary ---

  test("returns a non-empty US market summary with populated symbols") {
    YFinanceClient.resource[IO](config).use { client =>
      client.markets.getSummary(US).map { summary =>
        assertEquals(summary.region, US)
        assert(summary.nonEmpty, "US summary should be non-empty")
        summary.quotes.foreach { q =>
          assert(q.symbol.nonEmpty, s"symbol should be non-empty: $q")
        }
        assert(
          summary.quotes.exists(_.regularMarketPrice.isDefined),
          "at least one quote should carry a price"
        )
      }
    }
  }

  test("includes the S&P 500 in the US market summary") {
    YFinanceClient.resource[IO](config).use { client =>
      client.markets.getSummary(US).map { summary =>
        assert(
          summary.findBySymbol("^GSPC").isDefined,
          s"expected ^GSPC in summary; got ${summary.quotes.map(_.symbol).mkString(",")}"
        )
      }
    }
  }

  test("returns a non-empty UK market summary") {
    YFinanceClient.resource[IO](config).use { client =>
      client.markets.getSummary(GB).map { summary =>
        assert(summary.nonEmpty, "UK summary should be non-empty")
      }
    }
  }

  test("returns a summary for at least 3 of the major regions") {
    YFinanceClient.resource[IO](config).use { client =>
      majorRegions
        .traverse(r => client.markets.getSummary(r).attempt)
        .map { results =>
          val succeeded = results.count(_.isRight)
          assert(succeeded >= 3, s"expected ≥3 major regions to succeed; got $succeeded/${majorRegions.size}")
        }
    }
  }

  // --- getStatus ---

  test("returns a US market status with a non-empty status label") {
    YFinanceClient.resource[IO](config).use { client =>
      client.markets.getStatus(US).map { status =>
        assertEquals(status.region, US)
        assert(status.status.nonEmpty, "status label should be non-empty")
      }
    }
  }

  test("US market status carries a timezone abbreviation") {
    YFinanceClient.resource[IO](config).use { client =>
      client.markets.getStatus(US).map { status =>
        assert(
          status.timezone.short.nonEmpty,
          s"timezone abbreviation should be non-empty: ${status.timezone}"
        )
      }
    }
  }

  test("US market status close is after open") {
    YFinanceClient.resource[IO](config).use { client =>
      client.markets.getStatus(US).map { status =>
        assert(status.close.isAfter(status.open), s"close (${status.close}) should be after open (${status.open})")
      }
    }
  }

  test("US market session duration falls between 1 and 24 hours") {
    YFinanceClient.resource[IO](config).use { client =>
      client.markets.getStatus(US).map { status =>
        val d = status.sessionDuration
        assert(d >= 1.hour && d <= 24.hours, s"sessionDuration $d outside [1h, 24h]")
      }
    }
  }

  // --- getTrending ---

  test("returns at most 5 trending US tickers when requested") {
    YFinanceClient.resource[IO](config).use { client =>
      client.markets.getTrending(US, count = 5).map { trending =>
        assert(trending.nonEmpty, "trending list should not be empty")
        assert(trending.size <= 5, s"trending list larger than requested: ${trending.size}")
      }
    }
  }

  test("every trending ticker has a non-empty symbol") {
    YFinanceClient.resource[IO](config).use { client =>
      client.markets.getTrending(US).map { trending =>
        trending.foreach(t => assert(t.symbol.nonEmpty, s"symbol empty: $t"))
      }
    }
  }

  // --- getMarketSnapshot ---

  test("returns a populated snapshot for the US region") {
    YFinanceClient.resource[IO](config).use { client =>
      client.markets.getMarketSnapshot(US).map { snapshot =>
        assertEquals(snapshot.region, US)
        assert(snapshot.summary.nonEmpty, "snapshot summary should not be empty")
        assert(snapshot.trending.nonEmpty, "snapshot trending should not be empty")
        assert(snapshot.status.status.nonEmpty, "snapshot status.status should not be empty")
      }
    }
  }

  test("largest gainer and largest loser are disjoint when multiple quotes exist") {
    YFinanceClient.resource[IO](config).use { client =>
      client.markets.getSummary(US).map { summary =>
        if (summary.quotes.size > 1) {
          (summary.largestGainer, summary.largestLoser) match {
            case (Some(g), Some(l)) => assertNotEquals(g.symbol, l.symbol)
            case _                  => () // at least one side empty; nothing to assert
          }
        }
      }
    }
  }
}
