package io.github.coinductive.yfinance4s.integration

import cats.effect.IO
import munit.CatsEffectSuite
import io.github.coinductive.yfinance4s.{YFinanceClient, YFinanceClientConfig}
import io.github.coinductive.yfinance4s.models.*

import scala.concurrent.duration.*

class HistoryMetadataSpec extends CatsEffectSuite {

  override val munitTimeout: Duration = 60.seconds

  private val config: YFinanceClientConfig = YFinanceClientConfig(
    connectTimeout = 10.seconds,
    readTimeout = 30.seconds,
    retries = 1
  )

  test("getHistoryMetadata for AAPL returns equity metadata in the New York timezone") {
    YFinanceClient.resource[IO](config).use { client =>
      client.charts.getHistoryMetadata(Ticker("AAPL")).map { md =>
        assertEquals(md.instrumentType, InstrumentType.Equity)
        assertEquals(md.exchangeTimezoneName, "America/New_York")
        assertEquals(md.currency, "USD")
        assert(md.validRanges.contains(Range.`1Year`), s"validRanges should include 1y, got ${md.validRanges}")
        assert(md.regularMarketPrice.isDefined, "regularMarketPrice should be present for AAPL")
        assert(md.currentTradingPeriod.isDefined, "currentTradingPeriod should be present for AAPL")
      }
    }
  }

  test("getHistoryMetadata for SPY reports a fund instrument type") {
    YFinanceClient.resource[IO](config).use { client =>
      client.charts.getHistoryMetadata(Ticker("SPY")).map { md =>
        assert(md.instrumentType.isFund, s"SPY should be a fund, got ${md.instrumentType}")
      }
    }
  }

  test("getHistoryMetadata on an invalid ticker raises TickerNotFound") {
    val invalidTicker = Ticker("INVALIDTICKER123")
    YFinanceClient.resource[IO](config).use { client =>
      client.charts.getHistoryMetadata(invalidTicker).attempt.map {
        case Left(YFinanceError.TickerNotFound(t)) => assertEquals(t, invalidTicker)
        case other                                 => fail(s"expected TickerNotFound($invalidTicker), got $other")
      }
    }
  }
}
