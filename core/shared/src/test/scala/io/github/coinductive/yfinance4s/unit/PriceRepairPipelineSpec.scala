package io.github.coinductive.yfinance4s.unit

import cats.data.NonEmptyList
import munit.FunSuite
import io.github.coinductive.yfinance4s.PriceRepair
import io.github.coinductive.yfinance4s.PriceRepair.Reconstruction
import io.github.coinductive.yfinance4s.models.*
import io.github.coinductive.yfinance4s.models.internal.*

import java.time.{Instant, ZoneOffset, ZonedDateTime}

class PriceRepairPipelineSpec extends FunSuite {

  private val baseEpoch = 1704067200L // 2024-01-01T00:00:00Z
  private val daySeconds = 86400L
  private val ticker = Ticker("TEST")
  private val enabled = PriceRepairConfig.resolve(PriceRepairConfig.Enabled)

  private def utc(epochSeconds: Long): ZonedDateTime =
    ZonedDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC)

  private def epochAt(i: Int): Long = baseEpoch + i * daySeconds

  private def metaRaw(
      currency: String = "USD",
      regularMarketPrice: Option[Double] = None,
      regularMarketTime: Option[Long] = None
  ): ChartMetaRaw = ChartMetaRaw(
    currency = currency,
    symbol = "TEST",
    exchangeName = "NMS",
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

  /** A dividend event map for `chartOf`, keyed by the bar index the dividend falls on. */
  private def dividendsAt(entries: (Int, Double, Option[String])*): Events =
    Events(
      dividends = Some(entries.map { case (i, amount, currency) =>
        epochAt(i).toString -> DividendEventRaw(amount = amount, date = epochAt(i), currency = currency)
      }.toMap),
      splits = None
    )

  /** A daily chart of flat bars (all OHLC and adj close equal to the given price). */
  private def chartOf(
      prices: Vector[Double],
      meta: ChartMetaRaw = metaRaw(),
      events: Option[Events] = None
  ): Chart = {
    val timestamps = prices.indices.map(epochAt).toList
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
          events = events
        )
      )
    )
  }

  private def repairedResult(
      chart: Chart,
      interval: Interval = Interval.`1Day`,
      fxRates: Map[String, Double] = Map.empty
  ): ChartResult =
    PriceRepair
      .prepare(chart, ticker, interval, enabled, fxRates)
      .map(PriceRepair.complete(_, Reconstruction.empty))
      .getOrElse(fail("expected chart data"))

  private val planted100x = chartOf(Vector(10.0, 10.0, 10.0, 1000.0, 10.0, 10.0))

  // Chronological switch shape: the older half 100x too large.
  private val switchPrices = Vector.tabulate(60)(i => if (i < 30) 10000.0 else 100.0)

  test("repairs a planted 100x bar from a decoded chart") {
    val result = repairedResult(planted100x)
    assert(math.abs(result.quotes(3).close - 10.0) < 0.001, s"expected ~10.0, got ${result.quotes(3).close}")
    assert(result.quotes(3).repaired, "the planted bar should be flagged")
    assertEquals(result.quotes(0).close, 10.0)
    assertEquals(result.quotes.count(_.repaired), 1)
  }

  test("matches the plain mapping for a clean chart") {
    val events = Events(
      dividends = Some(Map(epochAt(2).toString -> DividendEventRaw(amount = 0.5, date = epochAt(2), currency = None))),
      splits = Some(
        Map(epochAt(4).toString -> SplitEventRaw(date = epochAt(4), numerator = 4, denominator = 1, splitRatio = "4:1"))
      )
    )
    val clean = chartOf(Vector(10.0, 10.5, 11.0, 10.8, 11.2, 11.5), events = Some(events))
    val result = repairedResult(clean)
    val expectedQuotes = Vector(10.0, 10.5, 11.0, 10.8, 11.2, 11.5).zipWithIndex.map { case (price, i) =>
      ChartResult.Quote(utc(epochAt(i)), price, price, 1000L, price, price, price)
    }.toList
    val expected = ChartResult(
      quotes = expectedQuotes,
      dividends = List(DividendEvent(utc(epochAt(2)), 0.5)),
      splits = List(SplitEvent(utc(epochAt(4)), 4, 1, "4:1")),
      currency = "USD"
    )
    assertEquals(result, expected)
  }

  test("preserves bar count, order and datetimes") {
    val result = repairedResult(planted100x)
    assertEquals(result.quotes.map(_.datetime), (0 to 5).toList.map(i => utc(epochAt(i))))
  }

  test("rebuilds a switch-scaled dividend into the dividends list") {
    val events = Events(
      dividends =
        Some(Map(epochAt(10).toString -> DividendEventRaw(amount = 500.0, date = epochAt(10), currency = None))),
      splits = None
    )
    val result = repairedResult(chartOf(switchPrices, events = Some(events)))
    assertEquals(result.dividends.map(_.exDate), List(utc(epochAt(10))))
    val amount = result.dividends.head.amount
    assert(math.abs(amount - 5.0) < 0.001, s"expected the dividend scaled to ~5.0, got $amount")
  }

  test("leaves the splits list intact") {
    val events = Events(
      dividends = None,
      splits = Some(
        Map(
          epochAt(50).toString -> SplitEventRaw(date = epochAt(50), numerator = 4, denominator = 1, splitRatio = "4:1")
        )
      )
    )
    val result = repairedResult(chartOf(switchPrices, events = Some(events)))
    assertEquals(result.splits, List(SplitEvent(utc(epochAt(50)), 4, 1, "4:1")))
    assert(math.abs(result.quotes.head.close - 100.0) < 0.001, "the switch repair should still run")
  }

  test("passes a 5d chart through unrepaired") {
    val result = repairedResult(planted100x, interval = Interval.`5Days`)
    assertEquals(result.quotes(3).close, 1000.0)
    assert(result.quotes.forall(!_.repaired), "no bar should be repaired for a 5d chart")
  }

  test("passes a weekly chart through unrepaired") {
    val result = repairedResult(planted100x, interval = Interval.`1Week`)
    assertEquals(result.quotes(3).close, 1000.0)
    assert(result.quotes.forall(!_.repaired), "no bar should be repaired for a weekly chart")
  }

  test("stamps the metadata currency on the result") {
    val result = repairedResult(chartOf(Vector(10.0, 10.5, 11.0, 10.8)))
    assertEquals(result.currency, "USD")
  }

  test("standardises a pence chart end to end") {
    val chart = chartOf(
      Vector(2450.0, 2480.0, 2500.0, 2460.0),
      meta = metaRaw(currency = "GBp"),
      events = Some(dividendsAt((2, 77.0, None)))
    )
    val result = repairedResult(chart)

    assertEquals(result.currency, "GBP")
    assertEquals(result.quotes.size, 4)
    assertEquals(result.quotes.map(_.datetime), (0 to 3).toList.map(i => utc(epochAt(i))))
    assert(math.abs(result.quotes(0).close - 24.50) < 1e-9, s"expected ~24.50, got ${result.quotes(0).close}")
    assert(math.abs(result.quotes(3).close - 24.60) < 1e-9, s"expected ~24.60, got ${result.quotes(3).close}")
    val dividend = result.dividends.head
    assert(math.abs(dividend.amount - 0.77) < 1e-9, s"expected the dividend in pounds, got ${dividend.amount}")
  }

  test("threads the raw currency into the switch factor after standardisation") {
    // Fils, with the older block reported 1000x too large: standardisation converts to dinar while the
    // switch repair still selects its factor from the raw KWF label.
    val prices = Vector.tabulate(60)(i => if (i < 30) 300000.0 else 300.0)
    val result = repairedResult(chartOf(prices, meta = metaRaw(currency = "KWF")))

    assertEquals(result.currency, "KWD")
    result.quotes.foreach { q =>
      assert(math.abs(q.close - 0.3) < 1e-9, s"expected every close ~0.3 dinar, got ${q.close} at ${q.datetime}")
    }
    assertEquals(result.quotes.zipWithIndex.filter(_._1.repaired).map(_._2), (0 until 30).toList)
  }

  test("applies a fetched rate to a foreign dividend end to end") {
    val chart = chartOf(
      Vector(10.0, 10.5, 11.0, 10.8),
      meta = metaRaw(currency = "GBP"),
      events = Some(dividendsAt((2, 1.0, Some("USD"))))
    )
    val result = repairedResult(chart, fxRates = Map("USD" -> 0.8))

    val dividend = result.dividends.head
    assert(math.abs(dividend.amount - 0.8) < 1e-9, s"expected the converted amount, got ${dividend.amount}")
    assertEquals(dividend.currency, Some("GBP"))
  }

  test("standardises a weekly pence chart without repairing it") {
    val chart = chartOf(Vector(2450.0, 2480.0, 248000.0, 2460.0), meta = metaRaw(currency = "GBp"))
    val result = repairedResult(chart, interval = Interval.`1Week`)

    assertEquals(result.currency, "GBP")
    assert(math.abs(result.quotes(0).close - 24.50) < 1e-9, s"expected ~24.50, got ${result.quotes(0).close}")
    assert(math.abs(result.quotes(2).close - 2480.0) < 1e-9, s"the outlier must survive, got ${result.quotes(2).close}")
    assert(result.quotes.forall(!_.repaired), "no bar is repaired on a weekly chart")
  }

  test("keeps raw amounts and labels on the unrepaired dividend path") {
    val chart = chartOf(
      Vector(2450.0, 2480.0, 2500.0, 2460.0),
      meta = metaRaw(currency = "GBp"),
      events = Some(dividendsAt((2, 77.0, Some("GBp"))))
    )
    val disabled = PriceRepairConfig.resolve(PriceRepairConfig.Disabled)
    val result = PriceRepair
      .prepare(chart, ticker, Interval.`1Day`, disabled)
      .map(PriceRepair.complete(_, Reconstruction.empty))
      .getOrElse(fail("expected chart data"))

    assertEquals(result.currency, "GBp")
    assertEquals(result.dividends.head.amount, 77.0)
    assertEquals(result.dividends.head.currency, Some("GBp"))
  }

  test("repairs a switch and a separate lone outlier in one pass") {
    val prices = switchPrices.updated(49, 10000.0) // lone 100x outlier inside the newer block
    val result = repairedResult(chartOf(prices))
    result.quotes.foreach { q =>
      assert(math.abs(q.close - 100.0) < 0.001, s"expected every close ~100.0, got ${q.close} at ${q.datetime}")
    }
    val repairedIdx = result.quotes.zipWithIndex.filter(_._1.repaired).map(_._2)
    assertEquals(repairedIdx, (0 until 30).toList :+ 49)
  }
}
