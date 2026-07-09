package org.coinductive.yfinance4s.unit

import cats.data.NonEmptyList
import munit.FunSuite
import org.coinductive.yfinance4s.PriceRepair
import org.coinductive.yfinance4s.models.{Interval, PriceRepairConfig, Ticker}
import org.coinductive.yfinance4s.models.internal.*

class PriceRepairConfigSpec extends FunSuite {

  private val baseEpoch = 1704067200L // 2024-01-01T00:00:00Z
  private val daySeconds = 86400L
  private val ticker = Ticker("TEST")

  private def metaRaw: ChartMetaRaw = ChartMetaRaw(
    currency = "USD",
    symbol = "TEST",
    exchangeName = "NMS",
    fullExchangeName = None,
    instrumentType = "EQUITY",
    firstTradeDate = None,
    regularMarketTime = None,
    gmtoffset = 0L,
    timezone = "UTC",
    exchangeTimezoneName = "UTC",
    regularMarketPrice = None,
    chartPreviousClose = None,
    priceHint = None,
    currentTradingPeriod = None,
    dataGranularity = "1d",
    range = "1mo",
    validRanges = None,
    hasPrePostMarketData = None
  )

  /** A daily chart of flat bars (all OHLC and adj close equal to the given price). */
  private def chartOf(prices: Vector[Double]): Chart = {
    val timestamps = prices.indices.map(i => baseEpoch + i * daySeconds).toList
    val values = prices.toList
    Chart(
      List(
        InstrumentData(
          meta = Some(metaRaw),
          timestamp = timestamps,
          indicators = Indicators(
            quote = NonEmptyList.one(
              Quote(close = values, open = values, volume = List.fill(prices.size)(1000L), high = values, low = values)
            ),
            adjclose = NonEmptyList.one(AdjClose(values))
          ),
          events = None
        )
      )
    )
  }

  // Carries both defect kinds: a 100x close at index 3 and a zero bar at index 5.
  private val mixedDefects = chartOf(Vector(10.0, 10.0, 10.0, 1000.0, 10.0, 0.0, 10.0))

  private def prepared(cfg: PriceRepairConfig.Custom): PriceRepair.Prepared =
    PriceRepair.prepare(mixedDefects, ticker, Interval.`1Day`, cfg).getOrElse(fail("expected a prepared pipeline"))

  test("fixes only 100x errors when zero repair is off") {
    val result = prepared(PriceRepairConfig.Custom(fix100xErrors = true, fixZeroes = false))
    assert(math.abs(result.bars(3).close - 10.0) < 0.001, s"expected the 100x bar fixed, got ${result.bars(3).close}")
    assert(result.bars(3).repaired, "the fixed bar should be flagged")
    assert(result.tags.isEmpty, "zero detection should be off")
  }

  test("detects only zeroes when 100x repair is off") {
    val result = prepared(PriceRepairConfig.Custom(fix100xErrors = false, fixZeroes = true))
    assertEquals(result.bars(3).close, 1000.0)
    assert(!result.bars(3).repaired, "the 100x bar should be untouched")
    assert(result.tags.priceCells.exists(_._1 == 5), s"expected the zero bar tagged, got ${result.tags}")
  }

  test("enabled resolves to every repair") {
    val result = prepared(PriceRepairConfig.resolve(PriceRepairConfig.Enabled))
    assert(math.abs(result.bars(3).close - 10.0) < 0.001, s"expected the 100x bar fixed, got ${result.bars(3).close}")
    assert(result.tags.priceCells.exists(_._1 == 5), s"expected the zero bar tagged, got ${result.tags}")
  }
}
