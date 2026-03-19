package org.coinductive.yfinance4s.models

/** Sort order for screener results. */
sealed trait SortOrder {
  private[yfinance4s] def apiValue: String
}

object SortOrder {
  case object Asc extends SortOrder { val apiValue = "ASC" }
  case object Desc extends SortOrder { val apiValue = "DESC" }
}

/** Configuration for a custom screener query.
  *
  * @param sortField
  *   Field to sort results by (e.g., `"ticker"`, `"percentchange"`, `"dayvolume"`)
  * @param sortOrder
  *   Sort direction
  * @param offset
  *   Number of results to skip (for pagination)
  * @param count
  *   Maximum number of results to return (max 250)
  */
final case class ScreenerConfig(
    sortField: String = ScreenerConfig.DefaultSortField,
    sortOrder: SortOrder = SortOrder.Desc,
    offset: Int = ScreenerConfig.DefaultOffset,
    count: Int = ScreenerConfig.DefaultCount
) {
  require(count >= 1 && count <= ScreenerConfig.MaxCount, s"count must be between 1 and ${ScreenerConfig.MaxCount}")
  require(offset >= 0, "offset must be non-negative")
}

object ScreenerConfig {
  val DefaultSortField = "ticker"
  val DefaultOffset = 0
  val DefaultCount = 25
  val MaxCount = 250

  val Default: ScreenerConfig = ScreenerConfig()
}

/** A single quote from a screener result.
  *
  * @param symbol
  *   Ticker symbol (e.g., "AAPL")
  * @param shortName
  *   Short display name
  * @param longName
  *   Full legal name
  * @param quoteType
  *   Instrument type ("EQUITY", "MUTUALFUND", etc.)
  * @param exchange
  *   Exchange code (e.g., "NMS", "NYQ")
  * @param exchangeDisplay
  *   Human-readable exchange name (e.g., "NASDAQ")
  * @param sector
  *   Business sector
  * @param industry
  *   Business industry
  * @param regularMarketPrice
  *   Current/last market price
  * @param regularMarketChange
  *   Absolute price change
  * @param regularMarketChangePercent
  *   Percentage price change
  * @param regularMarketVolume
  *   Trading volume
  * @param marketCap
  *   Market capitalization
  * @param trailingPE
  *   Trailing P/E ratio
  * @param forwardPE
  *   Forward P/E ratio
  * @param priceToBook
  *   Price-to-book ratio
  * @param fiftyTwoWeekHigh
  *   52-week high price
  * @param fiftyTwoWeekLow
  *   52-week low price
  * @param dividendYield
  *   Dividend yield (as decimal, e.g. 0.005 for 0.5%)
  * @param epsTrailingTwelveMonths
  *   Trailing 12-month earnings per share
  * @param averageDailyVolume3Month
  *   3-month average daily trading volume
  */
final case class ScreenerQuote(
    symbol: String,
    shortName: Option[String],
    longName: Option[String],
    quoteType: Option[String],
    exchange: Option[String],
    exchangeDisplay: Option[String],
    sector: Option[String],
    industry: Option[String],
    regularMarketPrice: Option[Double],
    regularMarketChange: Option[Double],
    regularMarketChangePercent: Option[Double],
    regularMarketVolume: Option[Long],
    marketCap: Option[Long],
    trailingPE: Option[Double],
    forwardPE: Option[Double],
    priceToBook: Option[Double],
    fiftyTwoWeekHigh: Option[Double],
    fiftyTwoWeekLow: Option[Double],
    dividendYield: Option[Double],
    epsTrailingTwelveMonths: Option[Double],
    averageDailyVolume3Month: Option[Long]
) {

  /** Best available display name: longName -> shortName -> symbol. */
  def displayName: String = longName.orElse(shortName).getOrElse(symbol)

  /** Converts this quote to a [[Ticker]]. */
  def toTicker: Ticker = Ticker(symbol)

  /** True if this is an equity/stock. */
  def isEquity: Boolean = quoteType.contains("EQUITY")

  /** True if this is a mutual fund. */
  def isMutualFund: Boolean = quoteType.contains("MUTUALFUND")

  /** True if this is an ETF. */
  def isETF: Boolean = quoteType.contains("ETF")

  /** Distance from 52-week high as a percentage (negative means below high). */
  def distanceFrom52WeekHigh: Option[Double] =
    for {
      price <- regularMarketPrice
      high <- fiftyTwoWeekHigh
      if high > 0
    } yield (price - high) / high * 100.0

  /** Distance from 52-week low as a percentage (positive means above low). */
  def distanceFrom52WeekLow: Option[Double] =
    for {
      price <- regularMarketPrice
      low <- fiftyTwoWeekLow
      if low > 0
    } yield (price - low) / low * 100.0
}

object ScreenerQuote {

  /** Orders quotes by market cap descending (largest first), with None-cap quotes last. */
  implicit val ordering: Ordering[ScreenerQuote] =
    Ordering.by[ScreenerQuote, Long](_.marketCap.getOrElse(0L)).reverse
}

/** Aggregate screener results from Yahoo Finance.
  *
  * @param quotes
  *   Matched securities
  * @param total
  *   Total number of matches on Yahoo's side (may be larger than `quotes.size` due to pagination)
  */
final case class ScreenerResult(
    quotes: List[ScreenerQuote],
    total: Int
) {

  /** True if any results were returned. */
  def nonEmpty: Boolean = quotes.nonEmpty

  /** True if no results were returned. */
  def isEmpty: Boolean = quotes.isEmpty

  /** Number of quotes in this page of results. */
  def size: Int = quotes.size

  /** True if there are more results available beyond this page. */
  def hasMore: Boolean = total > quotes.size

  /** Extracts all matched ticker symbols. */
  def symbols: List[String] = quotes.map(_.symbol)

  /** Extracts all matched tickers as [[Ticker]] values. */
  def tickers: List[Ticker] = quotes.map(_.toTicker)

  /** Filters to equity quotes only. */
  def equities: List[ScreenerQuote] = quotes.filter(_.isEquity)

  /** Filters to mutual fund quotes only. */
  def funds: List[ScreenerQuote] = quotes.filter(_.isMutualFund)

  /** Filters to ETF quotes only. */
  def etfs: List[ScreenerQuote] = quotes.filter(_.isETF)

  /** Returns the quote with the highest market cap, if any. */
  def largestByMarketCap: Option[ScreenerQuote] = quotes.sorted.headOption

  /** Returns quotes filtered to a specific sector. */
  def bySector(sector: String): List[ScreenerQuote] =
    quotes.filter(_.sector.contains(sector))

  /** Returns all distinct sectors present in the results. */
  def sectors: List[String] = quotes.flatMap(_.sector).distinct
}

object ScreenerResult {
  val empty: ScreenerResult = ScreenerResult(quotes = List.empty, total = 0)
}
