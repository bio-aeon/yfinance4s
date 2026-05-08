package org.coinductive.yfinance4s.unit

import munit.FunSuite
import org.coinductive.yfinance4s.models.*

class MarketSummarySpec extends FunSuite {

  // --- MarketIndexQuote ---

  private val gspcQuote = MarketIndexQuote(
    symbol = "^GSPC",
    shortName = Some("S&P 500"),
    exchange = Some("SNP"),
    marketState = Some("REGULAR"),
    regularMarketPrice = Some(5280.42),
    regularMarketChange = Some(12.13),
    regularMarketChangePercent = Some(0.023)
  )

  test("converts market change percent fraction to human-scale percent") {
    val percent = gspcQuote.regularMarketChangePercentAsPercent
    assert(percent.isDefined)
    assert(Math.abs(percent.get - 2.3) < 0.001)
  }

  test("returns no change percent when underlying value is absent") {
    val q = gspcQuote.copy(regularMarketChangePercent = None)
    assertEquals(q.regularMarketChangePercentAsPercent, None)
  }

  test("treats a regular-session quote as open") {
    assert(gspcQuote.copy(marketState = Some("REGULAR")).isOpen)
  }

  test("treats pre-market and after-hours quotes as open") {
    assert(gspcQuote.copy(marketState = Some("PRE")).isOpen)
    assert(gspcQuote.copy(marketState = Some("POST")).isOpen)
    assert(gspcQuote.copy(marketState = Some("PREPRE")).isOpen)
    assert(gspcQuote.copy(marketState = Some("POSTPOST")).isOpen)
  }

  test("treats market state case-insensitively when deciding open status") {
    assert(gspcQuote.copy(marketState = Some("regular")).isOpen)
    assert(gspcQuote.copy(marketState = Some("Pre")).isOpen)
  }

  test("treats a closed quote as not open") {
    assert(!gspcQuote.copy(marketState = Some("CLOSED")).isOpen)
  }

  test("treats a quote with missing market state as not open") {
    assert(!gspcQuote.copy(marketState = None).isOpen)
  }

  test("treats a closed quote as closed") {
    assert(gspcQuote.copy(marketState = Some("CLOSED")).isClosed)
    assert(gspcQuote.copy(marketState = Some("closed")).isClosed)
  }

  test("treats a regular-session quote as not closed") {
    assert(!gspcQuote.copy(marketState = Some("REGULAR")).isClosed)
  }

  test("treats a quote with missing market state as not closed") {
    assert(!gspcQuote.copy(marketState = None).isClosed)
  }

  // --- MarketIndexQuote.byChangePercentDesc ordering ---

  test("sorts quotes by change percent descending") {
    val a = gspcQuote.copy(symbol = "A", regularMarketChangePercent = Some(0.01))
    val b = gspcQuote.copy(symbol = "B", regularMarketChangePercent = Some(0.05))
    val c = gspcQuote.copy(symbol = "C", regularMarketChangePercent = Some(-0.02))
    val sorted = List(a, b, c).sorted(using MarketIndexQuote.byChangePercentDesc)
    assertEquals(sorted.map(_.symbol), List("B", "A", "C"))
  }

  test("sorts quotes with missing change percent last") {
    val a = gspcQuote.copy(symbol = "A", regularMarketChangePercent = None)
    val b = gspcQuote.copy(symbol = "B", regularMarketChangePercent = Some(0.05))
    val c = gspcQuote.copy(symbol = "C", regularMarketChangePercent = Some(-0.02))
    val sorted = List(a, b, c).sorted(using MarketIndexQuote.byChangePercentDesc)
    assertEquals(sorted.map(_.symbol), List("B", "C", "A"))
  }

  // --- MarketSummary ---

  private val region = MarketRegion("US")

  private val quotes = List(
    MarketIndexQuote(symbol = "^GSPC", exchange = Some("SNP"), regularMarketChangePercent = Some(0.01)),
    MarketIndexQuote(symbol = "^DJI", exchange = Some("DJI"), regularMarketChangePercent = Some(0.03)),
    MarketIndexQuote(symbol = "^IXIC", exchange = Some("NAS"), regularMarketChangePercent = Some(-0.02)),
    MarketIndexQuote(symbol = "^VIX", exchange = Some("CBO"), regularMarketChangePercent = Some(-0.05))
  )

  private val summary = MarketSummary(region = region, quotes = quotes)

  test("treats a summary with quotes as non-empty") {
    assert(summary.nonEmpty)
    assert(!summary.isEmpty)
  }

  test("treats a summary without quotes as empty") {
    val empty = MarketSummary(region = region)
    assert(empty.isEmpty)
    assert(!empty.nonEmpty)
  }

  test("finds quote by exact symbol match") {
    assertEquals(summary.findBySymbol("^DJI").map(_.symbol), Some("^DJI"))
  }

  test("treats symbol lookup as case-sensitive") {
    assertEquals(summary.findBySymbol("^dji"), None)
  }

  test("finds no quote for an unknown symbol") {
    assertEquals(summary.findBySymbol("NOT-A-SYMBOL"), None)
  }

  test("finds quote by exchange case-insensitively") {
    assertEquals(summary.findByExchange("snp").map(_.symbol), Some("^GSPC"))
    assertEquals(summary.findByExchange("NAS").map(_.symbol), Some("^IXIC"))
  }

  test("finds no quote for an unknown exchange") {
    assertEquals(summary.findByExchange("FOO"), None)
  }

  test("identifies the largest positive mover") {
    assertEquals(summary.largestGainer.map(_.symbol), Some("^DJI"))
  }

  test("returns no gainer when all quotes have negative or zero change") {
    val negOnly = MarketSummary(region, quotes.filter(_.regularMarketChangePercent.exists(_ < 0)))
    assertEquals(negOnly.largestGainer, None)
  }

  test("returns no gainer when no quotes have a change percent") {
    val noChange = MarketSummary(region, List(MarketIndexQuote("^X")))
    assertEquals(noChange.largestGainer, None)
  }

  test("identifies the most negative mover") {
    assertEquals(summary.largestLoser.map(_.symbol), Some("^VIX"))
  }

  test("returns no loser when all quotes have non-negative change") {
    val posOnly = MarketSummary(region, quotes.filter(_.regularMarketChangePercent.exists(_ >= 0)))
    assertEquals(posOnly.largestLoser, None)
  }

  test("returns movers whose absolute change meets or exceeds the threshold") {
    val movers3pct = summary.movers(0.03).map(_.symbol).toSet
    assertEquals(movers3pct, Set("^DJI", "^VIX"))
  }

  test("returns no movers when threshold exceeds all changes") {
    assertEquals(summary.movers(0.10), List.empty)
  }

  test("returns all quotes with a present change at zero threshold") {
    val all = summary.movers(0.0).map(_.symbol).toSet
    assertEquals(all, Set("^GSPC", "^DJI", "^IXIC", "^VIX"))
  }
}
