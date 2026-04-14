package org.coinductive.yfinance4s.unit

import munit.FunSuite
import org.coinductive.yfinance4s.models.*

import java.time.{ZoneOffset, ZonedDateTime}

class SearchResultSpec extends FunSuite {

  // --- QuoteSearchResult tests ---

  private val appleQuote = QuoteSearchResult(
    symbol = "AAPL",
    shortName = Some("Apple Inc."),
    longName = Some("Apple Inc."),
    quoteType = Some("EQUITY"),
    exchange = Some("NMS"),
    exchangeDisplay = Some("NASDAQ"),
    sector = Some("Technology"),
    industry = Some("Consumer Electronics"),
    score = Some(25305.0)
  )

  private val spyEtf = QuoteSearchResult(
    symbol = "SPY",
    shortName = Some("SPDR S&P 500 ETF Trust"),
    longName = Some("SPDR S&P 500 ETF Trust"),
    quoteType = Some("ETF"),
    exchange = Some("PCX"),
    exchangeDisplay = Some("NYSEArca"),
    sector = None,
    industry = None,
    score = Some(20000.0)
  )

  private val btcCrypto = QuoteSearchResult(
    symbol = "BTC-USD",
    shortName = Some("Bitcoin USD"),
    longName = Some("Bitcoin USD"),
    quoteType = Some("CRYPTOCURRENCY"),
    exchange = Some("CCC"),
    exchangeDisplay = Some("CCC"),
    sector = None,
    industry = None,
    score = Some(15000.0)
  )

  test("identifies EQUITY quote type correctly") {
    assert(appleQuote.isEquity)
    assert(!appleQuote.isETF)
    assert(!appleQuote.isIndex)
    assert(!appleQuote.isCryptocurrency)
  }

  test("identifies ETF quote type correctly") {
    assert(spyEtf.isETF)
    assert(!spyEtf.isEquity)
  }

  test("identifies CRYPTOCURRENCY quote type correctly") {
    assert(btcCrypto.isCryptocurrency)
    assert(!btcCrypto.isEquity)
  }

  test("identifies MUTUALFUND quote type correctly") {
    val fund = appleQuote.copy(quoteType = Some("MUTUALFUND"))
    assert(fund.isMutualFund)
  }

  test("identifies INDEX quote type correctly") {
    val index = appleQuote.copy(quoteType = Some("INDEX"))
    assert(index.isIndex)
  }

  test("identifies CURRENCY quote type correctly") {
    val currency = appleQuote.copy(quoteType = Some("CURRENCY"))
    assert(currency.isCurrency)
  }

  test("identifies FUTURE quote type correctly") {
    val future = appleQuote.copy(quoteType = Some("FUTURE"))
    assert(future.isFuture)
  }

  test("prefers longName for display") {
    assertEquals(appleQuote.displayName, "Apple Inc.")
  }

  test("falls back to shortName when longName is absent") {
    val noLong = appleQuote.copy(longName = None)
    assertEquals(noLong.displayName, "Apple Inc.")
  }

  test("falls back to symbol when both names are absent") {
    val noNames = appleQuote.copy(longName = None, shortName = None)
    assertEquals(noNames.displayName, "AAPL")
  }

  test("converts symbol to Ticker") {
    assertEquals(appleQuote.toTicker, Ticker("AAPL"))
  }

  test("sorts quotes by relevance score descending") {
    val quotes = List(
      appleQuote.copy(score = Some(100.0)),
      appleQuote.copy(symbol = "B", score = Some(500.0)),
      appleQuote.copy(symbol = "C", score = Some(250.0))
    )
    val sorted = quotes.sorted
    assertEquals(sorted.map(_.symbol), List("B", "C", "AAPL"))
  }

  test("ranks quotes without scores last") {
    val quotes = List(
      appleQuote.copy(symbol = "A", score = None),
      appleQuote.copy(symbol = "B", score = Some(100.0))
    )
    val sorted = quotes.sorted
    assertEquals(sorted.map(_.symbol), List("B", "A"))
  }

  test("all type checks return false when quoteType is None") {
    val noType = appleQuote.copy(quoteType = None)
    assert(!noType.isEquity)
    assert(!noType.isETF)
    assert(!noType.isIndex)
    assert(!noType.isMutualFund)
    assert(!noType.isCurrency)
    assert(!noType.isCryptocurrency)
    assert(!noType.isFuture)
  }

  // --- NewsItem tests ---

  private val newsItem = NewsItem(
    uuid = "abc-123",
    title = "Apple Reports Record Revenue",
    publisher = "Reuters",
    link = "https://example.com/article",
    publishTime = ZonedDateTime.of(2024, 6, 15, 10, 30, 0, 0, ZoneOffset.UTC),
    articleType = Some("STORY"),
    thumbnailUrl = Some("https://example.com/thumb.jpg"),
    relatedTickers = List("AAPL", "MSFT")
  )

  test("matches related tickers case-insensitively") {
    assert(newsItem.isRelatedTo("AAPL"))
    assert(newsItem.isRelatedTo("aapl"))
    assert(newsItem.isRelatedTo("MSFT"))
    assert(!newsItem.isRelatedTo("GOOGL"))
  }

  test("sorts news most recent first") {
    val older = newsItem.copy(
      uuid = "older",
      publishTime = ZonedDateTime.of(2024, 6, 14, 10, 0, 0, 0, ZoneOffset.UTC)
    )
    val newer = newsItem.copy(
      uuid = "newer",
      publishTime = ZonedDateTime.of(2024, 6, 16, 10, 0, 0, 0, ZoneOffset.UTC)
    )
    val sorted = List(older, newsItem, newer).sorted
    assertEquals(sorted.map(_.uuid), List("newer", "abc-123", "older"))
  }

  test("reports no related tickers when list is empty") {
    val noTickers = newsItem.copy(relatedTickers = List.empty)
    assert(!noTickers.isRelatedTo("AAPL"))
  }

  // --- SearchResult tests ---

  private val searchResult = SearchResult(
    quotes = List(appleQuote, spyEtf, btcCrypto),
    news = List(newsItem),
    lists = List.empty,
    totalResults = 3
  )

  test("is non-empty when quotes exist") {
    assert(searchResult.nonEmpty)
  }

  test("is empty when no quotes, news, or lists exist") {
    assert(SearchResult.empty.isEmpty)
  }

  test("is non-empty when only news exists") {
    val newsOnly = SearchResult(
      quotes = List.empty,
      news = List(newsItem),
      lists = List.empty,
      totalResults = 0
    )
    assert(newsOnly.nonEmpty)
  }

  test("filters equities from mixed quote types") {
    val equities = searchResult.equities
    assertEquals(equities.size, 1)
    assertEquals(equities.head.symbol, "AAPL")
  }

  test("filters ETFs from mixed quote types") {
    val etfs = searchResult.etfs
    assertEquals(etfs.size, 1)
    assertEquals(etfs.head.symbol, "SPY")
  }

  test("returns highest-scored quote as top result") {
    val top = searchResult.topQuote
    assert(top.isDefined)
    assertEquals(top.get.symbol, "AAPL")
  }

  test("returns no top quote when results are empty") {
    assertEquals(SearchResult.empty.topQuote, None)
  }

  test("extracts all ticker symbols from quotes") {
    assertEquals(searchResult.symbols, List("AAPL", "SPY", "BTC-USD"))
  }

  test("extracts all Ticker values from quotes") {
    assertEquals(searchResult.tickers, List(Ticker("AAPL"), Ticker("SPY"), Ticker("BTC-USD")))
  }

  test("derived accessors yield empty results on empty input") {
    val empty = SearchResult.empty
    assertEquals(empty.equities, List.empty[QuoteSearchResult])
    assertEquals(empty.etfs, List.empty[QuoteSearchResult])
    assertEquals(empty.topQuote, None)
    assertEquals(empty.symbols, List.empty[String])
    assertEquals(empty.tickers, List.empty[Ticker])
  }
}
