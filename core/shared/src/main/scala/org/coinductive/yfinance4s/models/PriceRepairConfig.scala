package org.coinductive.yfinance4s.models

/** Controls automatic detection and repair of currency-unit price errors in
  * [[org.coinductive.yfinance4s.Charts.getChart]].
  *
  * Yahoo Finance intermittently reports price bars in the wrong currency subunit (cents for dollars, pence for pounds),
  * i.e. exactly 100x off (or 1000x for currencies with a 1000-unit minor). Repair detects and corrects these. It is
  * best-effort and never fails a request: values that cannot be repaired confidently are returned unchanged.
  *
  * Repair applies to daily and intraday intervals; other intervals are returned unrepaired. Repaired bars carry
  * `repaired = true` on [[ChartResult.Quote]], and a repaired chart's dividends reflect any correction -
  * [[org.coinductive.yfinance4s.Charts.getDividends]] always reports Yahoo's raw amounts and can therefore disagree.
  */
sealed trait PriceRepairConfig

object PriceRepairConfig {

  /** No repair. `getChart` returns Yahoo's bars verbatim. This is the default. */
  case object Disabled extends PriceRepairConfig

  /** Every repair the library can currently perform. Equivalent to `Custom(fix100xErrors = true, fixZeroes = true)`;
    * widens automatically as future phases add repairs.
    */
  case object Enabled extends PriceRepairConfig

  /** Selective repair.
    *
    * @param fix100xErrors
    *   Detect and correct currency-subunit mixups: sporadic 100x/0.01x outliers and systematic unit switches. Gates
    *   both the random-outlier and the sudden-switch repairs.
    * @param fixZeroes
    *   Detect zero/NaN prices that should carry a trade and reconstruct them from finer-grained bars. Detection is
    *   active now; reconstruction activates when interval reconstruction is available - until then this flag is a safe
    *   no-op.
    */
  final case class Custom(
      fix100xErrors: Boolean = true,
      fixZeroes: Boolean = true
  ) extends PriceRepairConfig

  private[yfinance4s] def resolve(config: PriceRepairConfig): Custom =
    config match {
      case Disabled  => Custom(fix100xErrors = false, fixZeroes = false)
      case Enabled   => Custom(fix100xErrors = true, fixZeroes = true)
      case c: Custom => c
    }
}
