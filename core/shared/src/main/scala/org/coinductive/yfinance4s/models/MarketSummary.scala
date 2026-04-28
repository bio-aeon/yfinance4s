package org.coinductive.yfinance4s.models

import java.time.ZonedDateTime

/** A single quote in a [[MarketSummary]]. Only `symbol` is structurally guaranteed by Yahoo's
  * `/v6/finance/quote/marketSummary` response - all other fields depend on the `fields=` request and the instrument's
  * trading state.
  */
final case class MarketIndexQuote(
    symbol: String,
    shortName: Option[String] = None,
    fullExchangeName: Option[String] = None,
    exchange: Option[String] = None,
    currency: Option[String] = None,
    marketState: Option[String] = None,
    regularMarketPrice: Option[Double] = None,
    regularMarketChange: Option[Double] = None,
    regularMarketChangePercent: Option[Double] = None,
    regularMarketTime: Option[ZonedDateTime] = None,
    previousClose: Option[Double] = None,
    exchangeTimezoneName: Option[String] = None
) {

  def toTicker: Ticker = Ticker(symbol)

  /** Market change as a percentage (e.g., `0.023` → `2.3`). */
  def regularMarketChangePercentAsPercent: Option[Double] =
    regularMarketChangePercent.map(_ * 100)

  /** True when Yahoo reports the instrument as actively trading. */
  def isOpen: Boolean =
    marketState.exists(s => MarketIndexQuote.OpenStates.contains(s.toUpperCase))

  /** True when Yahoo explicitly reports the instrument as closed. */
  def isClosed: Boolean =
    marketState.exists(_.equalsIgnoreCase(MarketIndexQuote.ClosedState))
}

object MarketIndexQuote {

  private val OpenStates: Set[String] = Set("REGULAR", "PRE", "POST", "PREPRE", "POSTPOST")
  private val ClosedState: String = "CLOSED"

  /** Sorted by `regularMarketChangePercent` descending; `None` values last. */
  implicit val byChangePercentDesc: Ordering[MarketIndexQuote] =
    Ordering
      .by[MarketIndexQuote, Option[Double]](_.regularMarketChangePercent)(using Ordering[Option[Double]].reverse)
}

/** A region's market-summary snapshot: headline indices and instruments. Ordered as Yahoo returned them. */
final case class MarketSummary(
    region: MarketRegion,
    quotes: List[MarketIndexQuote] = List.empty
) {

  def nonEmpty: Boolean = quotes.nonEmpty
  def isEmpty: Boolean = quotes.isEmpty

  /** First quote whose symbol matches exactly (case-sensitive). */
  def findBySymbol(symbol: String): Option[MarketIndexQuote] =
    quotes.find(_.symbol == symbol)

  /** First quote whose `exchange` matches (case-insensitive). */
  def findByExchange(exchange: String): Option[MarketIndexQuote] =
    quotes.find(_.exchange.exists(_.equalsIgnoreCase(exchange)))

  /** The quote with the highest positive `regularMarketChangePercent`. */
  def largestGainer: Option[MarketIndexQuote] =
    quotes
      .filter(_.regularMarketChangePercent.exists(_ > 0))
      .sorted(using MarketIndexQuote.byChangePercentDesc)
      .headOption

  /** The quote with the lowest (most-negative) `regularMarketChangePercent`. */
  def largestLoser: Option[MarketIndexQuote] =
    quotes
      .filter(_.regularMarketChangePercent.exists(_ < 0))
      .sortBy(_.regularMarketChangePercent)(using Ordering[Option[Double]])
      .headOption

  /** Quotes whose absolute change percent meets or exceeds the threshold (fraction, e.g., `0.02` for ±2%). */
  def movers(threshold: Double): List[MarketIndexQuote] =
    quotes.filter(_.regularMarketChangePercent.exists(c => math.abs(c) >= threshold))
}
