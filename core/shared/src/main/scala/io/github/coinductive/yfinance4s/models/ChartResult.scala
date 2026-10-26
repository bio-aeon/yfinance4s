package io.github.coinductive.yfinance4s.models

import io.github.coinductive.yfinance4s.models.ChartResult.Quote

import java.time.ZonedDateTime

/** Historical chart data with OHLCV quotes and corporate actions.
  *
  * This is the primary result type for historical price data queries. It includes both price/volume data and any
  * dividend or split events that occurred during the requested time period.
  *
  * @param quotes
  *   List of OHLCV price quotes, sorted chronologically. A timestamp for which Yahoo reports no prices yields no quote;
  *   a dividend or split on it is still listed. The row Yahoo adds for its latest price is folded into the quote it
  *   falls in, or left out where it lies outside a requested date range.
  * @param dividends
  *   List of dividend events within the chart period, sorted chronologically.
  * @param splits
  *   List of stock split events within the chart period, sorted chronologically.
  * @param currency
  *   The trading currency of the quotes as Yahoo reports it, after any standardisation applied via
  *   [[PriceRepairConfig]] (e.g. "GBP" for a pence-quoted chart standardised from "GBp").
  */
final case class ChartResult(
    quotes: List[Quote],
    dividends: List[DividendEvent] = List.empty,
    splits: List[SplitEvent] = List.empty,
    currency: String
) {

  /** Returns corporate actions as a combined object.
    */
  def corporateActions: CorporateActions = CorporateActions(dividends, splits)

  /** Whether any corporate actions occurred during this chart period.
    */
  def hasCorporateActions: Boolean = dividends.nonEmpty || splits.nonEmpty
}

object ChartResult {

  /** A single OHLCV price quote.
    *
    * @param datetime
    *   The timestamp for this quote (typically market close time).
    * @param close
    *   Closing price.
    * @param open
    *   Opening price.
    * @param volume
    *   Trading volume (number of shares traded); 0 where Yahoo reports none.
    * @param high
    *   Highest price during the period.
    * @param low
    *   Lowest price during the period.
    * @param adjclose
    *   Adjusted closing price (accounts for dividends and splits); equal to `close` where Yahoo reports none, which
    *   includes every bar of an intraday chart.
    * @param repaired
    *   Whether any O/H/L/C/Adj Close or volume value in this bar was altered by price repair (see
    *   [[PriceRepairConfig]]). Always false when repair is disabled.
    * @param inProgress
    *   Whether this daily bar's session was still open at the latest price Yahoo reported in the same response: its
    *   close is that price, and its high, low and volume may still change. Always false on charts of other intervals.
    */
  final case class Quote(
      datetime: ZonedDateTime,
      close: Double,
      open: Double,
      volume: Long,
      high: Double,
      low: Double,
      adjclose: Double,
      repaired: Boolean = false,
      inProgress: Boolean = false
  )
}
