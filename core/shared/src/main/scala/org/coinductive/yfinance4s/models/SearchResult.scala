package org.coinductive.yfinance4s.models

import java.time.ZonedDateTime

/** A single quote result from a Yahoo Finance search.
  *
  * @param symbol
  *   The ticker symbol (e.g., "AAPL")
  * @param shortName
  *   Short display name (e.g., "Apple Inc.")
  * @param longName
  *   Full legal name (e.g., "Apple Inc.")
  * @param quoteType
  *   The instrument type (e.g., "EQUITY", "ETF", "INDEX", "MUTUALFUND", "CURRENCY", "CRYPTOCURRENCY")
  * @param exchange
  *   The exchange code (e.g., "NMS")
  * @param exchangeDisplay
  *   Human-readable exchange name (e.g., "NASDAQ")
  * @param sector
  *   Business sector, if available (e.g., "Technology")
  * @param industry
  *   Business industry, if available (e.g., "Consumer Electronics")
  * @param score
  *   Relevance score from Yahoo's ranking algorithm
  */
final case class QuoteSearchResult(
    symbol: String,
    shortName: Option[String],
    longName: Option[String],
    quoteType: Option[String],
    exchange: Option[String],
    exchangeDisplay: Option[String],
    sector: Option[String],
    industry: Option[String],
    score: Option[Double]
) {

  /** True if this is an equity/stock. */
  def isEquity: Boolean = quoteType.contains("EQUITY")

  /** True if this is an ETF. */
  def isETF: Boolean = quoteType.contains("ETF")

  /** True if this is an index. */
  def isIndex: Boolean = quoteType.contains("INDEX")

  /** True if this is a mutual fund. */
  def isMutualFund: Boolean = quoteType.contains("MUTUALFUND")

  /** True if this is a currency pair. */
  def isCurrency: Boolean = quoteType.contains("CURRENCY")

  /** True if this is a cryptocurrency. */
  def isCryptocurrency: Boolean = quoteType.contains("CRYPTOCURRENCY")

  /** True if this is a futures contract. */
  def isFuture: Boolean = quoteType.contains("FUTURE")

  /** Returns the best available display name: longName, then shortName, then symbol. */
  def displayName: String =
    longName.orElse(shortName).getOrElse(symbol)

  /** Converts this quote result to a [[Ticker]]. */
  def toTicker: Ticker = Ticker(symbol)
}

object QuoteSearchResult {
  implicit val ordering: Ordering[QuoteSearchResult] =
    Ordering.by[QuoteSearchResult, Double](_.score.getOrElse(0.0)).reverse
}

/** A news article from Yahoo Finance search results.
  *
  * @param uuid
  *   Unique identifier for the article
  * @param title
  *   Article headline
  * @param publisher
  *   Name of the publishing source
  * @param link
  *   URL to the full article
  * @param publishTime
  *   When the article was published
  * @param articleType
  *   Article type (e.g., "STORY")
  * @param thumbnailUrl
  *   URL to the highest-resolution thumbnail, if available
  * @param relatedTickers
  *   Ticker symbols mentioned in or related to the article
  */
final case class NewsItem(
    uuid: String,
    title: String,
    publisher: String,
    link: String,
    publishTime: ZonedDateTime,
    articleType: Option[String],
    thumbnailUrl: Option[String],
    relatedTickers: List[String]
) extends Ordered[NewsItem] {

  override def compare(that: NewsItem): Int =
    that.publishTime.compareTo(this.publishTime)

  /** True if this news item is related to the given ticker symbol. */
  def isRelatedTo(symbol: String): Boolean =
    relatedTickers.exists(_.equalsIgnoreCase(symbol))
}

object NewsItem {
  implicit val ordering: Ordering[NewsItem] = Ordering.fromLessThan(_.compare(_) < 0)
}

/** A curated list from Yahoo Finance search results (e.g., "Most Active", "Day Gainers"). */
final case class SearchList(
    slug: String,
    title: String,
    description: Option[String],
    canonicalName: Option[String]
)

/** Aggregate search results from Yahoo Finance.
  *
  * @param quotes
  *   Matched ticker/company results, filtered to Yahoo Finance securities only
  * @param news
  *   Related news articles
  * @param lists
  *   Related curated lists
  * @param totalResults
  *   Total result count reported by Yahoo
  */
final case class SearchResult(
    quotes: List[QuoteSearchResult],
    news: List[NewsItem],
    lists: List[SearchList],
    totalResults: Int
) {

  /** True if any results were returned. */
  def nonEmpty: Boolean = quotes.nonEmpty || news.nonEmpty || lists.nonEmpty

  /** True if no results were returned. */
  def isEmpty: Boolean = !nonEmpty

  /** Filters quotes to only equities. */
  def equities: List[QuoteSearchResult] = quotes.filter(_.isEquity)

  /** Filters quotes to only ETFs. */
  def etfs: List[QuoteSearchResult] = quotes.filter(_.isETF)

  /** Returns the best-matching quote (highest relevance score), if any. */
  def topQuote: Option[QuoteSearchResult] = quotes.sorted.headOption

  /** Extracts all matched ticker symbols. */
  def symbols: List[String] = quotes.map(_.symbol)

  /** Extracts all matched tickers as [[Ticker]] values. */
  def tickers: List[Ticker] = quotes.map(_.toTicker)
}

object SearchResult {
  val empty: SearchResult = SearchResult(
    quotes = List.empty,
    news = List.empty,
    lists = List.empty,
    totalResults = 0
  )
}
