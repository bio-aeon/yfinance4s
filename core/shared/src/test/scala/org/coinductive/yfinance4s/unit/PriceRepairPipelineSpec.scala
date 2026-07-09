package org.coinductive.yfinance4s.unit

import cats.data.NonEmptyList
import munit.FunSuite
import org.coinductive.yfinance4s.PriceRepair
import org.coinductive.yfinance4s.PriceRepair.Reconstruction
import org.coinductive.yfinance4s.models.*
import org.coinductive.yfinance4s.models.internal.*

import java.time.{Instant, ZoneOffset, ZonedDateTime}

class PriceRepairPipelineSpec extends FunSuite {

  private val baseEpoch = 1704067200L // 2024-01-01T00:00:00Z
  private val daySeconds = 86400L
  private val ticker = Ticker("TEST")
  private val enabled = PriceRepairConfig.resolve(PriceRepairConfig.Enabled)

  private def utc(epochSeconds: Long): ZonedDateTime =
    ZonedDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC)

  private def epochAt(i: Int): Long = baseEpoch + i * daySeconds

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
  private def chartOf(
      prices: Vector[Double],
      meta: Option[ChartMetaRaw] = Some(metaRaw),
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

  private def repairedResult(chart: Chart, interval: Interval = Interval.`1Day`): ChartResult =
    PriceRepair
      .prepare(chart, ticker, interval, enabled)
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
      dividends = Some(Map(epochAt(2).toString -> DividendEventRaw(amount = 0.5, date = epochAt(2)))),
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
      splits = List(SplitEvent(utc(epochAt(4)), 4, 1, "4:1"))
    )
    assertEquals(result, expected)
  }

  test("preserves bar count, order and datetimes") {
    val result = repairedResult(planted100x)
    assertEquals(result.quotes.map(_.datetime), (0 to 5).toList.map(i => utc(epochAt(i))))
  }

  test("rebuilds a switch-scaled dividend into the dividends list") {
    val events = Events(
      dividends = Some(Map(epochAt(10).toString -> DividendEventRaw(amount = 500.0, date = epochAt(10)))),
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

  test("passes a chart without meta through unrepaired") {
    val result = repairedResult(chartOf(Vector(10.0, 10.0, 10.0, 1000.0, 10.0, 10.0), meta = None))
    assertEquals(result.quotes(3).close, 1000.0)
    assert(result.quotes.forall(!_.repaired), "repair must never invent a currency")
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
