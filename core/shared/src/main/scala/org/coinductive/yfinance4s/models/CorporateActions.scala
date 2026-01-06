package org.coinductive.yfinance4s.models

/** Aggregates all corporate actions for a ticker within a time period.
  *
  * Corporate actions are significant events initiated by a company that affect its shareholders, such as dividend
  * payments and stock splits.
  *
  * @param dividends
  *   List of dividend events, sorted chronologically by ex-date.
  * @param splits
  *   List of stock split events, sorted chronologically by ex-date.
  */
final case class CorporateActions(
    dividends: List[DividendEvent],
    splits: List[SplitEvent]
) {

  /** Whether there are any corporate actions in this result.
    */
  def isEmpty: Boolean = dividends.isEmpty && splits.isEmpty

  /** Whether there are any corporate actions in this result.
    */
  def nonEmpty: Boolean = !isEmpty

  /** Total sum of all dividend amounts.
    */
  def totalDividendAmount: Double = dividends.map(_.amount).sum

  /** Cumulative split factor over the period. Multiply a pre-period share count by this factor to get post-period
    * equivalent.
    */
  def cumulativeSplitFactor: Double =
    if (splits.isEmpty) 1.0 else splits.map(_.factor).product

  /** Number of dividend payments.
    */
  def dividendCount: Int = dividends.size

  /** Number of stock splits.
    */
  def splitCount: Int = splits.size
}

object CorporateActions {

  /** Empty corporate actions with no dividends or splits.
    */
  val empty: CorporateActions = CorporateActions(List.empty, List.empty)
}
