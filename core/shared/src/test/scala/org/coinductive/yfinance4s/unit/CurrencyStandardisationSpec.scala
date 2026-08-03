package org.coinductive.yfinance4s.unit

import munit.FunSuite
import org.coinductive.yfinance4s.{CurrencyStandardisation, PriceRepair}
import org.coinductive.yfinance4s.models.internal.ChartMetaRaw

import java.time.{Instant, ZoneOffset, ZonedDateTime}

class CurrencyStandardisationSpec extends FunSuite {

  private val baseEpoch = 1704067200L // 2024-01-01T00:00:00Z
  private val daySeconds = 86400L

  private def epochAt(i: Int): Long = baseEpoch + i * daySeconds

  private def utc(epochSeconds: Long): ZonedDateTime =
    ZonedDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC)

  /** A flat bar: all prices equal the given close. */
  private def bar(
      index: Int,
      close: Double,
      volume: Long = 1000L,
      dividend: Double = 0.0,
      dividendCurrency: Option[String] = None
  ): PriceRepair.Bar =
    PriceRepair.Bar(
      datetime = utc(epochAt(index)),
      open = close,
      high = close,
      low = close,
      close = close,
      adjClose = close,
      volume = volume,
      dividend = dividend,
      splitRatio = 0.0,
      repaired = false,
      dividendCurrency = dividendCurrency
    )

  private def barsOf(closes: Double*): Vector[PriceRepair.Bar] =
    closes.toVector.zipWithIndex.map { case (close, i) => bar(i, close) }

  private def meta(
      currency: String,
      regularMarketPrice: Option[Double] = None,
      regularMarketTime: Option[Long] = None
  ): ChartMetaRaw = ChartMetaRaw(
    currency = currency,
    symbol = "TEST",
    exchangeName = "LSE",
    fullExchangeName = None,
    instrumentType = "EQUITY",
    firstTradeDate = None,
    regularMarketTime = regularMarketTime,
    gmtoffset = 0L,
    timezone = "UTC",
    exchangeTimezoneName = "UTC",
    regularMarketPrice = regularMarketPrice,
    chartPreviousClose = None,
    priceHint = None,
    currentTradingPeriod = None,
    dataGranularity = "1d",
    range = "1mo",
    validRanges = None,
    hasPrePostMarketData = None
  )

  private def assertClose(actual: Double, expected: Double, clue: String): Unit =
    assert(math.abs(actual - expected) < 1e-9, s"$clue: expected ~$expected, got $actual")

  private val penceCloses = barsOf(2450.0, 2480.0, 2500.0, 2460.0)
  private val poundCloses = barsOf(24.50, 24.80, 25.00, 24.60)

  test("converts pence prices to pounds and relabels the chart") {
    val (bars, currency) = CurrencyStandardisation.standardise(penceCloses, meta("GBp"))

    assertEquals(currency, "GBP")
    assertClose(bars(0).close, 24.50, "close")
    assertClose(bars(3).close, 24.60, "close")
    bars.zip(penceCloses).foreach { case (converted, original) =>
      assertClose(converted.open, original.open * 0.01, "open")
      assertClose(converted.high, original.high * 0.01, "high")
      assertClose(converted.low, original.low * 0.01, "low")
      assertClose(converted.adjClose, original.adjClose * 0.01, "adj close")
      assertEquals(converted.volume, original.volume, "volume must not be scaled")
      assert(!converted.repaired, "standardisation is a unit conversion, not a repair")
    }
  }

  test("converts rand cents and shekel agora at one hundred to one") {
    val (randBars, randCurrency) = CurrencyStandardisation.standardise(barsOf(1500.0, 1520.0), meta("ZAc"))
    assertEquals(randCurrency, "ZAR")
    assertClose(randBars(0).close, 15.00, "rand close")

    val (shekelBars, shekelCurrency) = CurrencyStandardisation.standardise(barsOf(880.0, 900.0), meta("ILA"))
    assertEquals(shekelCurrency, "ILS")
    assertClose(shekelBars(1).close, 9.00, "shekel close")
  }

  test("converts Kuwaiti fils at one thousand to one") {
    val (bars, currency) = CurrencyStandardisation.standardise(barsOf(300000.0, 310000.0), meta("KWF"))

    assertEquals(currency, "KWD")
    assertClose(bars(0).close, 300.0, "dinar close")
    assertClose(bars(1).close, 310.0, "dinar close")
  }

  test("leaves a major-currency chart untouched") {
    val bars = barsOf(100.0, 101.0, 102.0)
    val (result, currency) = CurrencyStandardisation.standardise(bars, meta("USD"))

    assertEquals(currency, "USD")
    assertEquals(result, bars)
  }

  test("leaves everything untouched when no bar has volume") {
    val bars = penceCloses.map(_.copy(volume = 0L))
    val (result, currency) = CurrencyStandardisation.standardise(bars, meta("GBp"))

    assertEquals(currency, "GBp", "without a traded bar there is nothing to calibrate against")
    assertEquals(result, bars)
  }

  test("skips the rescale when the live price says the bars are already major") {
    val anchorEpoch = epochAt(3)
    val (bars, currency) = CurrencyStandardisation.standardise(
      poundCloses,
      meta("GBp", regularMarketPrice = Some(2460.0), regularMarketTime = Some(anchorEpoch + 3600L))
    )

    assertEquals(currency, "GBP", "the chart is relabelled even when the prices need no rescale")
    assertEquals(bars, poundCloses)
  }

  test("rescales when the live price matches the bars") {
    val anchorEpoch = epochAt(3)
    val (bars, currency) = CurrencyStandardisation.standardise(
      penceCloses,
      meta("GBp", regularMarketPrice = Some(2460.0), regularMarketTime = Some(anchorEpoch + 3600L))
    )

    assertEquals(currency, "GBP")
    assertClose(bars(3).close, 24.60, "close")
  }

  test("ignores the live-price check when the last print is stale") {
    val staleTime = epochAt(3) + 90 * daySeconds
    val (bars, _) = CurrencyStandardisation.standardise(
      poundCloses,
      meta("GBp", regularMarketPrice = Some(2460.0), regularMarketTime = Some(staleTime))
    )

    assertClose(bars(3).close, 0.246, "a stale print cannot prove the bars are already major")
  }

  test("anchors the live-price check on the newest bar that traded") {
    // The newest two bars are untraded and 0.01x the rest - the corruption the anchor rule exists for.
    val bars = Vector(
      bar(0, 2460.0),
      bar(1, 2460.0),
      bar(2, 2460.0),
      bar(3, 2460.0),
      bar(4, 24.60, volume = 0L),
      bar(5, 24.60, volume = 0L)
    )
    val (result, currency) = CurrencyStandardisation.standardise(
      bars,
      meta("GBp", regularMarketPrice = Some(2460.0), regularMarketTime = Some(epochAt(3) + 3600L))
    )

    assertEquals(currency, "GBP")
    assertClose(result(3).close, 24.60, "anchoring on the last traded bar keeps the rescale")
  }

  test("scales unlabelled dividends whose average yield is ridiculous") {
    val bars = penceCloses.updated(2, bar(2, 2500.0, dividend = 77.0))
    val (result, _) = CurrencyStandardisation.standardise(bars, meta("GBp"))

    assertClose(result(2).dividend, 0.77, "a pence dividend converts with the prices")
    assertEquals(result(2).dividendCurrency, None, "an unlabelled dividend stays unlabelled")
  }

  test("leaves unlabelled dividends with normal yields untouched") {
    val bars = penceCloses.updated(2, bar(2, 2500.0, dividend = 0.77))
    val (result, _) = CurrencyStandardisation.standardise(bars, meta("GBp"))

    assertClose(result(2).dividend, 0.77, "a dividend already in pounds must not be scaled again")
  }

  test("computes the dividend yield against the previous close") {
    // Own close would give 1.5 / 10.0 = 0.15 (silent); previous close gives 1.5 / 1.0 = 1.5 (fires).
    val bars = Vector(bar(0, 100.0), bar(1, 1000.0, dividend = 1.5))
    val (result, _) = CurrencyStandardisation.standardise(bars, meta("GBp"))

    assertClose(result(1).dividend, 0.015, "the yield must use the previous close")
  }

  test("carries the last known close across missing bars for the yield check") {
    val bars = Vector(
      bar(0, 200.0),
      bar(1, Double.NaN),
      bar(2, Double.NaN, dividend = 150.0),
      bar(3, 300.0)
    )
    val (result, _) = CurrencyStandardisation.standardise(bars, meta("GBp"))

    // prevClose(2) forward-fills to bar 0's rescaled close of 2.0, so the yield is 75.
    assertClose(result(2).dividend, 1.5, "the ffilled close drives the verdict")
  }

  test("standardises a subunit-labelled dividend without the heuristic") {
    val bars = penceCloses.updated(2, bar(2, 2500.0, dividend = 0.77, dividendCurrency = Some("GBp")))
    val (result, currency) = CurrencyStandardisation.standardise(bars, meta("GBp"))

    assertEquals(currency, "GBP")
    assertClose(result(2).dividend, 0.0077, "the label decides, whatever the yield looks like")
    assertEquals(result(2).dividendCurrency, Some("GBP"))
  }

  test("converts a subunit-labelled dividend on a chart outside the table") {
    val bars = barsOf(100.0, 101.0, 102.0).updated(1, bar(1, 101.0, dividend = 50.0, dividendCurrency = Some("GBp")))
    val (result, currency) = CurrencyStandardisation.standardise(bars, meta("USD"))

    assertEquals(currency, "USD", "the price currency is untouched")
    assertClose(result(0).close, 100.0, "prices must not move on a major-currency chart")
    assertClose(result(1).dividend, 0.5, "a pence-labelled dividend converts regardless of the price currency")
    assertEquals(result(1).dividendCurrency, Some("GBP"))
  }

  test("excludes foreign-labelled dividends from the yield heuristic") {
    val bars = penceCloses
      .updated(1, bar(1, 2480.0, dividend = 0.5))
      .updated(2, bar(2, 2500.0, dividend = 100.0, dividendCurrency = Some("USD")))
    val (result, _) = CurrencyStandardisation.standardise(bars, meta("GBp"))

    assertClose(result(1).dividend, 0.5, "the unlabelled yield is normal, so nothing scales")
    assertClose(result(2).dividend, 100.0, "a foreign dividend belongs to FX conversion, not the heuristic")
    assertEquals(result(2).dividendCurrency, Some("USD"))
  }
}
