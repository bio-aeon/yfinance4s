package org.coinductive.yfinance4s.models

import org.coinductive.yfinance4s.models.ChartResult.Quote

import java.time.ZonedDateTime

/** Historical chart data with OHLCV quotes and corporate actions.
  *
  * This is the primary result type for historical price data queries. It includes both price/volume data and any
  * dividend or split events that occurred during the requested time period.
  *
  * @param quotes
  *   List of OHLCV price quotes, sorted chronologically.
  * @param dividends
  *   List of dividend events within the chart period, sorted chronologically.
  * @param splits
  *   List of stock split events within the chart period, sorted chronologically.
  */
final case class ChartResult(
    quotes: List[Quote],
    dividends: List[DividendEvent] = List.empty,
    splits: List[SplitEvent] = List.empty
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
    *   Trading volume (number of shares traded).
    * @param high
    *   Highest price during the period.
    * @param low
    *   Lowest price during the period.
    * @param adjclose
    *   Adjusted closing price (accounts for dividends and splits).
    */
  final case class Quote(
      datetime: ZonedDateTime,
      close: Double,
      open: Double,
      volume: Long,
      high: Double,
      low: Double,
      adjclose: Double
  )
}
