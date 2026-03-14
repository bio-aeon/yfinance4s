package org.coinductive.yfinance4s.integration

import cats.effect.IO
import munit.CatsEffectSuite
import org.coinductive.yfinance4s.{YFinanceClient, YFinanceClientConfig}
import org.coinductive.yfinance4s.models.*

import scala.concurrent.duration.*

class ScreenerSpec extends CatsEffectSuite {

  val config: YFinanceClientConfig = YFinanceClientConfig(
    connectTimeout = 10.seconds,
    readTimeout = 30.seconds,
    retries = 3
  )

  // --- Predefined screen tests ---

  test("predefined DayGainers returns equity results") {
    YFinanceClient.resource[IO](config).use { client =>
      client.screener.screenPredefined(PredefinedScreen.DayGainers).map { result =>
        assert(result.nonEmpty, "Day gainers should return results")
        assert(result.total > 0, "Total should be positive")
        result.quotes.foreach { q =>
          assert(q.symbol.nonEmpty, "Each quote should have a symbol")
        }
      }
    }
  }

  test("predefined MostActives returns results with volume data") {
    YFinanceClient.resource[IO](config).use { client =>
      client.screener.screenPredefined(PredefinedScreen.MostActives).map { result =>
        assert(result.nonEmpty, "Most actives should return results")
        // Most active stocks should have volume data
        val withVolume = result.quotes.filter(_.regularMarketVolume.isDefined)
        assert(withVolume.nonEmpty, "Some quotes should have volume data")
      }
    }
  }

  test("predefined screen respects count parameter") {
    YFinanceClient.resource[IO](config).use { client =>
      client.screener.screenPredefined(PredefinedScreen.DayGainers, count = 5).map { result =>
        assert(result.quotes.size <= 5, s"Should have at most 5 quotes, got ${result.quotes.size}")
      }
    }
  }

  test("predefined TopMutualFunds returns fund results") {
    YFinanceClient.resource[IO](config).use { client =>
      client.screener.screenPredefined(PredefinedScreen.TopMutualFunds).map { result =>
        assert(result.nonEmpty, "Top mutual funds should return results")
      }
    }
  }

  // --- Custom equity screen tests ---

  test("custom equity screen with simple filter returns results") {
    YFinanceClient.resource[IO](config).use { client =>
      val query = EquityQuery.and(
        EquityQuery.eq("region", "us"),
        EquityQuery.gte("intradaymarketcap", 2000000000L)
      )
      client.screener.screenEquities(query).map { result =>
        assert(result.nonEmpty, "Large-cap US equities should return results")
        assert(result.total > 0, "Total should be positive")
      }
    }
  }

  test("custom equity screen returns quotes with expected structure") {
    YFinanceClient.resource[IO](config).use { client =>
      val query = EquityQuery.and(
        EquityQuery.gt("percentchange", 3),
        EquityQuery.eq("region", "us"),
        EquityQuery.gte("intradaymarketcap", 2000000000L),
        EquityQuery.gte("intradayprice", 5),
        EquityQuery.gt("dayvolume", 15000)
      )
      val screenerConfig = ScreenerConfig(sortField = "percentchange", sortOrder = SortOrder.Desc, count = 5)
      client.screener.screenEquities(query, screenerConfig).map { result =>
        result.quotes.foreach { q =>
          assert(q.symbol.nonEmpty, "Symbol should not be empty")
          assert(q.displayName.nonEmpty, "Display name should not be empty")
        }
      }
    }
  }

  test("custom equity screen respects count config") {
    YFinanceClient.resource[IO](config).use { client =>
      val query = EquityQuery.and(
        EquityQuery.eq("region", "us"),
        EquityQuery.gte("intradaymarketcap", 2000000000L)
      )
      val screenerConfig = ScreenerConfig(count = 3)
      client.screener.screenEquities(query, screenerConfig).map { result =>
        assert(result.quotes.size <= 3, s"Should have at most 3 quotes, got ${result.quotes.size}")
      }
    }
  }

  test("custom equity screen with between filter returns results") {
    YFinanceClient.resource[IO](config).use { client =>
      val query = EquityQuery.and(
        EquityQuery.between("peratio.lasttwelvemonths", 0, 20),
        EquityQuery.isIn("exchange", Seq(ScreenerValue("NMS"), ScreenerValue("NYQ")))
      )
      client.screener.screenEquities(query, ScreenerConfig(count = 5)).map { result =>
        assert(result.nonEmpty, "Low-PE exchange-filtered screen should return results")
      }
    }
  }

  // --- Custom fund screen tests ---

  test("custom fund screen returns results") {
    YFinanceClient.resource[IO](config).use { client =>
      val query = FundQuery.and(
        FundQuery.eq("exchange", "NAS"),
        FundQuery.gt("intradayprice", 15)
      )
      client.screener.screenFunds(query, ScreenerConfig(count = 5)).map { result =>
        assert(result.nonEmpty, "Fund screen should return results")
      }
    }
  }

  // --- Convenience method tests ---

  test("extracts symbols and tickers from live results") {
    YFinanceClient.resource[IO](config).use { client =>
      client.screener.screenPredefined(PredefinedScreen.MostActives, count = 3).map { result =>
        assert(result.symbols.nonEmpty, "symbols should not be empty")
        assert(result.tickers.nonEmpty, "tickers should not be empty")
        assertEquals(result.symbols.size, result.tickers.size)
      }
    }
  }

  test("detects more results when total exceeds page size") {
    YFinanceClient.resource[IO](config).use { client =>
      client.screener.screenPredefined(PredefinedScreen.MostActives, count = 1).map { result =>
        if (result.total > 1) {
          assert(result.hasMore, s"total=${result.total} > size=1, should have more")
        }
      }
    }
  }
}
