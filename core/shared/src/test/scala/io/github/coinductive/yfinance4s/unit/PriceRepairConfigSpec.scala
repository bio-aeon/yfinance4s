package io.github.coinductive.yfinance4s.unit

import cats.data.NonEmptyList
import munit.FunSuite
import io.github.coinductive.yfinance4s.PriceRepair
import io.github.coinductive.yfinance4s.models.{Interval, PriceRepairConfig, Ticker}
import io.github.coinductive.yfinance4s.models.internal.*

class PriceRepairConfigSpec extends FunSuite {

  private val baseEpoch = 1704067200L // 2024-01-01T00:00:00Z
  private val daySeconds = 86400L
  private val ticker = Ticker("TEST")

  private def metaRaw(currency: String = "USD"): ChartMetaRaw = ChartMetaRaw(
    currency = currency,
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
  private def chartOf(prices: Vector[Double], meta: ChartMetaRaw = metaRaw()): Chart = {
    val timestamps = prices.indices.map(i => baseEpoch + i * daySeconds).toList
    val values = prices.toList
    Chart(
      List(
        InstrumentData(
          meta = meta,
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

  // A pence chart carrying a 100x close at index 3, for the currency-versus-repair gating rows.
  private val penceWith100x =
    chartOf(Vector(2450.0, 2480.0, 2500.0, 250000.0, 2460.0, 2470.0), meta = metaRaw(currency = "GBp"))

  private def prepared(cfg: PriceRepairConfig.Custom, chart: Chart = mixedDefects): PriceRepair.Prepared =
    PriceRepair.prepare(chart, ticker, Interval.`1Day`, cfg).getOrElse(fail("expected a prepared pipeline"))

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

    val currencyResult = prepared(PriceRepairConfig.resolve(PriceRepairConfig.Enabled), penceWith100x)
    assertEquals(currencyResult.currency, "GBP", "enabled must standardise as well as repair")
    assert(
      math.abs(currencyResult.bars(3).close - 25.0) < 1e-9,
      s"expected the 100x bar fixed in pounds, got ${currencyResult.bars(3).close}"
    )
  }

  test("standardises without repairing when only standardisation is on") {
    val result = prepared(
      PriceRepairConfig.Custom(
        fix100xErrors = false,
        fixZeroes = false,
        standardiseCurrency = true,
        convertDividendFx = false
      ),
      penceWith100x
    )

    assertEquals(result.currency, "GBP")
    assert(math.abs(result.bars(0).close - 24.50) < 1e-9, s"expected ~24.50, got ${result.bars(0).close}")
    assert(math.abs(result.bars(3).close - 2500.0) < 1e-9, s"the outlier must survive, got ${result.bars(3).close}")
    assert(result.bars.forall(!_.repaired), "no bar is repaired when the error repairs are off")
  }

  test("repairs without standardising when standardisation is off") {
    val result = prepared(
      PriceRepairConfig.Custom(
        fix100xErrors = true,
        fixZeroes = false,
        standardiseCurrency = false,
        convertDividendFx = false
      ),
      penceWith100x
    )

    assertEquals(result.currency, "GBp", "prices stay in pence")
    assert(math.abs(result.bars(0).close - 2450.0) < 1e-9, s"expected pence, got ${result.bars(0).close}")
    assert(math.abs(result.bars(3).close - 2500.0) < 1e-9, s"expected the outlier fixed, got ${result.bars(3).close}")
    assert(result.bars(3).repaired, "the fixed bar should be flagged")
  }
}
