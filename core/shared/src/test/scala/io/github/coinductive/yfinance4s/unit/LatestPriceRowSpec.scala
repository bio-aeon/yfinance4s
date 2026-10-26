package io.github.coinductive.yfinance4s.unit

import munit.FunSuite
import io.github.coinductive.yfinance4s.LatestPriceRow
import io.github.coinductive.yfinance4s.LatestPriceRow.Window
import io.github.coinductive.yfinance4s.PriceRepair.Bar
import io.github.coinductive.yfinance4s.models.Interval
import io.github.coinductive.yfinance4s.models.internal.{ChartMetaRaw, TradingPeriodRaw, TradingPeriodsRaw}

import java.time.{Instant, ZoneOffset, ZonedDateTime}

class LatestPriceRowSpec extends FunSuite {

  /** 2026-10-23 20:00 UTC: SPY's closing print, and its latest one over the weekend that followed. */
  private val spyFridayClose = 1792785600L

  private def day(year: Int, month: Int, dayOfMonth: Int): ZonedDateTime =
    ZonedDateTime.of(year, month, dayOfMonth, 0, 0, 0, 0, ZoneOffset.UTC)

  private def bar(epochSecond: Long, open: Double, high: Double, low: Double, close: Double, volume: Long): Bar =
    Bar(
      datetime = ZonedDateTime.ofInstant(Instant.ofEpochSecond(epochSecond), ZoneOffset.UTC),
      open = open,
      high = high,
      low = low,
      close = close,
      adjClose = close,
      volume = volume,
      dividend = 0.0,
      splitRatio = 0.0,
      repaired = false
    )

  /** Yahoo's latest-price row on an intraday chart: one flat print without volume. */
  private def print(epochSecond: Long, price: Double): Bar = bar(epochSecond, price, price, price, price, 0L)

  /** A chart `meta` block whose latest print came at the given epoch second, for an exchange `gmtoffset` seconds off
    * UTC (New York in summer unless given) that reports `session` as its current regular session; nothing else in it
    * matters here.
    */
  private def metaAt(
      latestPrint: Long,
      gmtoffset: Long = -14400L,
      session: Option[TradingPeriodRaw] = None
  ): ChartMetaRaw = ChartMetaRaw(
    currency = "USD",
    symbol = "SPY",
    exchangeName = "PCX",
    fullExchangeName = None,
    instrumentType = "ETF",
    firstTradeDate = None,
    regularMarketTime = Some(latestPrint),
    gmtoffset = gmtoffset,
    timezone = "EDT",
    exchangeTimezoneName = "America/New_York",
    regularMarketPrice = None,
    chartPreviousClose = None,
    priceHint = None,
    currentTradingPeriod = session.map(regular => TradingPeriodsRaw(pre = None, regular = Some(regular), post = None)),
    dataGranularity = "1h",
    range = "",
    validRanges = None,
    hasPrePostMarketData = None
  )

  /** SPY's last two 30-minute bars of the 2026-10-23 session, which ended at 20:00 UTC. */
  private val spyHalfHourBars = Vector(
    bar(1792782000L, 760.6, 760.91, 760.21, 760.5, 1812634L),
    bar(1792783800L, 760.49, 760.54, 759.55, 759.91, 6515703L)
  )

  test("folds a flat print inside the last hourly bar into it") {
    val earlier = bar(1793035800L, 765.8, 766.3099, 764.455, 765.64, 2112876L)
    val last = bar(1793039400L, 765.64, 765.8, 765.37, 765.43, 161520L)
    val rows = Vector(earlier, last, print(1793039853L, 765.5))
    assertEquals(
      LatestPriceRow.settle(rows, Interval.`1Hour`, metaAt(1793039853L), None),
      Vector(earlier, last.copy(close = 765.5, adjClose = 765.5))
    )
  }

  test("folds the closing print at the end of the last 30-minute bar into it") {
    val rows = spyHalfHourBars :+ print(spyFridayClose, 759.97)
    assertEquals(
      LatestPriceRow.settle(rows, Interval.`30Minutes`, metaAt(spyFridayClose), None),
      spyHalfHourBars.init :+ spyHalfHourBars.last.copy(close = 759.97, adjClose = 759.97)
    )
  }

  test("keeps a 30-minute bar that starts where the one before it ends") {
    assertEquals(
      LatestPriceRow.settle(spyHalfHourBars, Interval.`30Minutes`, metaAt(spyFridayClose), None),
      spyHalfHourBars
    )
  }

  test("folds the closing print at the session end into the last 30-minute bar, though later prints followed") {
    val last = bar(1793030400L, 118.5, 118.5275, 118.15, 118.45, 1538581L)
    val londonSession = TradingPeriodRaw(timezone = "GMT", start = 1793001600L, end = 1793032200L, gmtoffset = 0L)
    val meta = metaAt(1793035719L, gmtoffset = 0L, session = Some(londonSession))
    assertEquals(
      LatestPriceRow.settle(Vector(last, print(1793032200L, 118.25)), Interval.`30Minutes`, meta, None),
      Vector(last.copy(close = 118.25, adjClose = 118.25))
    )
  }

  test("keeps a currency pair's flat minute bar that starts where the one before it ends") {
    val minutes = Vector(
      bar(1793049360L, 1.11807, 1.11807, 1.11807, 1.11807, 0L),
      bar(1793049420L, 1.11794, 1.11794, 1.11794, 1.11794, 0L)
    )
    val fxSession = TradingPeriodRaw(timezone = "GMT", start = 1792972800L, end = 1793059140L, gmtoffset = 0L)
    val meta = metaAt(1793049526L, gmtoffset = 0L, session = Some(fxSession))
    assertEquals(LatestPriceRow.settle(minutes, Interval.`1Minute`, meta, None), minutes)
  }

  test("leaves out the row Yahoo appends after a requested window") {
    val bars = Vector(
      bar(1785522600L, 746.04, 747.92, 745.67, 747.49, 7842744L),
      bar(1785526200L, 747.49, 748.895, 746.34, 746.81, 10315583L)
    )
    val window = Window(day(2026, 7, 27), day(2026, 8, 1))
    assertEquals(
      LatestPriceRow
        .settle(bars :+ print(spyFridayClose, 759.97), Interval.`1Hour`, metaAt(spyFridayClose), Some(window)),
      bars
    )
  }

  test("leaves out the latest session Yahoo returns before a requested window") {
    val friday = bar(spyFridayClose, 759.896, 763.47, 758.68, 759.97, 33133505L)
    val window = Window(day(2026, 10, 24), day(2026, 10, 26))
    assertEquals(
      LatestPriceRow.settle(Vector(friday), Interval.`1Day`, metaAt(spyFridayClose), Some(window)),
      Vector.empty
    )
  }

  test("keeps a bar stamped before a requested window that is not the latest print") {
    val newZealandBars = Vector(
      bar(1785103200L, 0.43, 0.43, 0.415, 0.425, 928329L),
      bar(1785189600L, 0.425, 0.435, 0.42, 0.42, 445203L)
    )
    val window = Window(day(2026, 7, 27), day(2026, 8, 1))
    assertEquals(
      LatestPriceRow.settle(newZealandBars, Interval.`1Day`, metaAt(1792727997L), Some(window)),
      newZealandBars
    )
  }

  test("folds the latest session into a week bar that already holds it, keeping the week's volume") {
    val week = bar(1792382400L, 767.23, 774.44, 758.68, 759.97, 193167100L)
    val friday = bar(spyFridayClose, 759.896, 763.47, 758.68, 759.97, 33133505L)
    assertEquals(
      LatestPriceRow.settle(Vector(week, friday), Interval.`1Week`, metaAt(spyFridayClose), None),
      Vector(week)
    )
  }

  test("adds the latest session to a month bar Yahoo has not rolled it into yet") {
    val month = bar(1790827200L, 764.36, 781.62, 758.68, 759.97, 670044400L)
    val today = bar(1793039856L, 762.25, 767.73, 761.64, 765.46, 17357177L)
    assertEquals(
      LatestPriceRow.settle(Vector(month, today), Interval.`1Month`, metaAt(1793039856L), None),
      Vector(month.copy(close = 765.46, adjClose = 765.46, volume = 687401577L))
    )
  }

  test("completes a week bar Yahoo sends without its close, keeping the week's volume") {
    val week = bar(1792940400L, 3072.0, 3137.0, 3069.0, Double.NaN, 7039300L)
    val today = bar(1792980804L, 3072.0, 3137.0, 3069.0, 3128.0, 7039300L)
    assertEquals(
      LatestPriceRow.settle(Vector(week, today), Interval.`1Week`, metaAt(1792980804L, gmtoffset = 32400L), None),
      Vector(week.copy(close = 3128.0, adjClose = 3128.0))
    )
  }

  test("keeps a week bar stamped an hour earlier after daylight saving time begins") {
    val weeks = Vector(
      bar(1772427600L, 678.7, 688.62, 669.66, 672.38, 478956000L),
      bar(1773028800L, 666.39, 683.36, 661.36, 662.29, 458697100L)
    )
    assertEquals(LatestPriceRow.settle(weeks, Interval.`1Week`, metaAt(1793044800L), None), weeks)
  }

  test("folds the month's last session into a month bar stamped before daylight saving time ended") {
    val october = bar(1790809200L, 122.0, 129.0, 115.95, 118.2, 1057793358L)
    val lastSession = bar(1793377800L, 118.55, 118.951, 117.2, 118.25, 24183489L)
    assertEquals(
      LatestPriceRow.settle(Vector(october, lastSession), Interval.`1Month`, metaAt(1793377800L, gmtoffset = 0L), None),
      Vector(october.copy(close = 118.25, adjClose = 118.25, volume = 1081976847L))
    )
  }
}
