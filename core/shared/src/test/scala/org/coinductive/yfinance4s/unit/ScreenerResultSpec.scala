package org.coinductive.yfinance4s.unit

import munit.FunSuite
import org.coinductive.yfinance4s.models.*

class ScreenerResultSpec extends FunSuite {

  // --- ScreenerQuote tests ---

  private val appleQuote = ScreenerQuote(
    symbol = "AAPL",
    shortName = Some("Apple Inc."),
    longName = Some("Apple Inc."),
    quoteType = Some("EQUITY"),
    exchange = Some("NMS"),
    exchangeDisplay = Some("NASDAQ"),
    sector = Some("Technology"),
    industry = Some("Consumer Electronics"),
    regularMarketPrice = Some(227.48),
    regularMarketChange = Some(3.12),
    regularMarketChangePercent = Some(1.39),
    regularMarketVolume = Some(54321000L),
    marketCap = Some(3450000000000L),
    trailingPE = Some(37.5),
    forwardPE = Some(32.1),
    priceToBook = Some(52.3),
    fiftyTwoWeekHigh = Some(260.1),
    fiftyTwoWeekLow = Some(164.08),
    dividendYield = Some(0.0044),
    epsTrailingTwelveMonths = Some(6.07),
    averageDailyVolume3Month = Some(48500000L)
  )

  private val msftQuote = appleQuote.copy(
    symbol = "MSFT",
    shortName = Some("Microsoft Corporation"),
    longName = Some("Microsoft Corporation"),
    marketCap = Some(3200000000000L),
    sector = Some("Technology")
  )

  private val fundQuote = appleQuote.copy(
    symbol = "VFINX",
    shortName = Some("Vanguard 500 Index Fund"),
    longName = None,
    quoteType = Some("MUTUALFUND"),
    sector = None,
    industry = None,
    marketCap = None
  )

  test("identifies EQUITY quote type correctly") {
    assert(appleQuote.isEquity)
    assert(!appleQuote.isMutualFund)
    assert(!appleQuote.isETF)
  }

  test("identifies MUTUALFUND quote type correctly") {
    assert(fundQuote.isMutualFund)
    assert(!fundQuote.isEquity)
  }

  test("identifies ETF quote type correctly") {
    val etf = appleQuote.copy(quoteType = Some("ETF"))
    assert(etf.isETF)
    assert(!etf.isEquity)
  }

  test("all type checks return false when quoteType is None") {
    val noType = appleQuote.copy(quoteType = None)
    assert(!noType.isEquity)
    assert(!noType.isMutualFund)
    assert(!noType.isETF)
  }

  test("prefers longName for display") {
    assertEquals(appleQuote.displayName, "Apple Inc.")
  }

  test("falls back to shortName when longName is absent") {
    assertEquals(fundQuote.displayName, "Vanguard 500 Index Fund")
  }

  test("falls back to symbol when both names are absent") {
    val noNames = appleQuote.copy(longName = None, shortName = None)
    assertEquals(noNames.displayName, "AAPL")
  }

  test("converts symbol to Ticker") {
    assertEquals(appleQuote.toTicker, Ticker("AAPL"))
  }

  test("sorts quotes by market cap descending") {
    val smallCap = appleQuote.copy(symbol = "SMALL", marketCap = Some(1000000L))
    val quotes = List(smallCap, appleQuote, msftQuote)
    val sorted = quotes.sorted
    assertEquals(sorted.map(_.symbol), List("AAPL", "MSFT", "SMALL"))
  }

  test("quotes without marketCap sort last") {
    val noCap = appleQuote.copy(symbol = "NOCAP", marketCap = None)
    val quotes = List(noCap, appleQuote)
    val sorted = quotes.sorted
    assertEquals(sorted.map(_.symbol), List("AAPL", "NOCAP"))
  }

  test("52-week high distance is negative when price is below high") {
    // price=227.48, high=260.1 => (227.48 - 260.1) / 260.1 * 100 ≈ -12.54%
    val dist = appleQuote.distanceFrom52WeekHigh
    assert(dist.isDefined)
    assert(dist.get < 0, "Should be negative (below 52-week high)")
    assertEqualsDouble(dist.get, -12.5413, 0.01)
  }

  test("52-week low distance is positive when price is above low") {
    // price=227.48, low=164.08 => (227.48 - 164.08) / 164.08 * 100 ≈ 38.64%
    val dist = appleQuote.distanceFrom52WeekLow
    assert(dist.isDefined)
    assert(dist.get > 0, "Should be positive (above 52-week low)")
    assertEqualsDouble(dist.get, 38.6402, 0.01)
  }

  test("distance metrics return None when price is missing") {
    val noPrice = appleQuote.copy(regularMarketPrice = None)
    assertEquals(noPrice.distanceFrom52WeekHigh, None)
    assertEquals(noPrice.distanceFrom52WeekLow, None)
  }

  test("distance metrics return None when 52-week data is missing") {
    val noHigh = appleQuote.copy(fiftyTwoWeekHigh = None)
    assertEquals(noHigh.distanceFrom52WeekHigh, None)
  }

  // --- ScreenerResult tests ---

  private val result = ScreenerResult(
    quotes = List(appleQuote, msftQuote, fundQuote),
    total = 150
  )

  test("is non-empty when quotes exist") {
    assert(result.nonEmpty)
  }

  test("is empty when no quotes exist") {
    assert(ScreenerResult.empty.isEmpty)
  }

  test("reports correct size") {
    assertEquals(result.size, 3)
  }

  test("detects more results available") {
    assert(result.hasMore, "total=150 > size=3, should have more")
    assert(!ScreenerResult(List(appleQuote), 1).hasMore)
  }

  test("extracts all ticker symbols") {
    assertEquals(result.symbols, List("AAPL", "MSFT", "VFINX"))
  }

  test("extracts all Ticker values") {
    assertEquals(result.tickers, List(Ticker("AAPL"), Ticker("MSFT"), Ticker("VFINX")))
  }

  test("filters equities from mixed results") {
    val equities = result.equities
    assertEquals(equities.size, 2)
    assertEquals(equities.map(_.symbol), List("AAPL", "MSFT"))
  }

  test("filters funds from mixed results") {
    val funds = result.funds
    assertEquals(funds.size, 1)
    assertEquals(funds.head.symbol, "VFINX")
  }

  test("returns largest by market cap") {
    val largest = result.largestByMarketCap
    assert(largest.isDefined)
    assertEquals(largest.get.symbol, "AAPL")
  }

  test("filters by sector") {
    val tech = result.bySector("Technology")
    assertEquals(tech.size, 2)
  }

  test("returns distinct sectors") {
    assertEquals(result.sectors, List("Technology"))
  }

  test("derived accessors yield empty results on empty input") {
    val empty = ScreenerResult.empty
    assertEquals(empty.symbols, List.empty[String])
    assertEquals(empty.equities, List.empty[ScreenerQuote])
    assertEquals(empty.largestByMarketCap, None)
    assertEquals(empty.sectors, List.empty[String])
  }

  // --- ScreenerConfig tests ---

  test("rejects count above max") {
    intercept[IllegalArgumentException] {
      ScreenerConfig(count = 251)
    }
  }

  test("rejects count below 1") {
    intercept[IllegalArgumentException] {
      ScreenerConfig(count = 0)
    }
  }

  test("rejects negative offset") {
    intercept[IllegalArgumentException] {
      ScreenerConfig(offset = -1)
    }
  }

  test("accepts count at max boundary") {
    val config = ScreenerConfig(count = 250)
    assertEquals(config.count, 250)
  }

  // --- SortOrder tests ---

  test("ascending sort maps to ASC") {
    assertEquals(SortOrder.Asc.apiValue, "ASC")
  }

  test("descending sort maps to DESC") {
    assertEquals(SortOrder.Desc.apiValue, "DESC")
  }

  // --- PredefinedScreen tests ---

  test("all predefined screens have unique IDs") {
    val ids = PredefinedScreen.values.map(_.screenId)
    assertEquals(ids.size, ids.distinct.size)
  }

  test("looks up predefined screen by ID") {
    assertEquals(PredefinedScreen.fromScreenId("day_gainers"), Some(PredefinedScreen.DayGainers))
  }

  test("returns None for unknown screen ID") {
    assertEquals(PredefinedScreen.fromScreenId("nonexistent"), None)
  }

  test("equity screens and fund screens partition all values") {
    val all = (PredefinedScreen.equityScreens ++ PredefinedScreen.fundScreens).toSet
    assertEquals(all, PredefinedScreen.values.toSet)
  }
}
